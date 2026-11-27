import { useFocusEffect, useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { format } from 'date-fns';
import { useCallback, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { areaApi } from '../../api/endpoints';
import { errorMessage } from '../../api/errors';
import {
  Button,
  Card,
  EmptyState,
  ErrorBanner,
  ListRow,
  LoadingBlock,
  Screen,
  StatusChip,
} from '../../components';
import type { AreaStackParamList } from '../../navigation/types';
import { FieldVisit, listVisits, mergeServerVisits } from '../../services/visitLog';
import { spacing } from '../../theme/tokens';

type Nav = NativeStackNavigationProp<AreaStackParamList>;

export function FieldVisitsScreen() {
  const navigation = useNavigation<Nav>();
  const [visits, setVisits] = useState<FieldVisit[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const remote = await areaApi.fieldVisits({ size: 50 });
      const merged = await mergeServerVisits(remote.content ?? []);
      setVisits(merged);
    } catch (e) {
      const local = await listVisits();
      setVisits(local);
      setError(`Showing local visits only — ${errorMessage(e)}`);
    } finally {
      setLoading(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      void load();
    }, [load]),
  );

  return (
    <Screen
      title="Field visits"
      subtitle="Check-ins synced to the backend for your area"
      onRefresh={() => load()}
      refreshing={loading}>
      <Button label="New check-in" onPress={() => navigation.navigate('CheckIn', {})} style={styles.cta} />
      {error ? <ErrorBanner message={error} /> : null}
      {loading ? <LoadingBlock label="Loading visits…" /> : null}
      {!loading && visits.length === 0 ? (
        <EmptyState
          title="No visits yet"
          message="Record a check-in to sync a field visit to the platform."
        />
      ) : null}
      <View style={styles.list}>
        {visits.map((v) => (
          <Card key={v.id} style={styles.card}>
            <ListRow
              title={v.agentName || v.agentCode || v.agentId}
              subtitle={`${v.note}\n${format(new Date(v.createdAt), 'dd MMM yyyy HH:mm')} · ${v.latitude.toFixed(4)}, ${v.longitude.toFixed(4)}`}
            />
            <View style={styles.chips}>
              <StatusChip label={v.synced ? 'Synced' : 'Not synced'} tone={v.synced ? 'success' : 'warning'} />
              {v.simulated ? <StatusChip label="Simulated GPS" tone="warning" /> : null}
            </View>
          </Card>
        ))}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  cta: { marginBottom: spacing.md },
  list: { gap: spacing.sm },
  card: { paddingHorizontal: spacing.md, paddingBottom: spacing.sm, gap: spacing.sm },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm, paddingBottom: spacing.sm },
});

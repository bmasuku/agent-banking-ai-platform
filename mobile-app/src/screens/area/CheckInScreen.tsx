import { RouteProp, useNavigation, useRoute } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { areaApi } from '../../api/endpoints';
import { errorMessage } from '../../api/errors';
import {
  Button,
  Card,
  ErrorBanner,
  LoadingBlock,
  Screen,
  StatusChip,
  TextField,
} from '../../components';
import type { AreaStackParamList } from '../../navigation/types';
import { DeviceLocation, requestForegroundLocation } from '../../services/location';
import { addVisit, markVisitSynced, markVisitSyncFailed } from '../../services/visitLog';
import { colors, spacing, typography } from '../../theme/tokens';

type Nav = NativeStackNavigationProp<AreaStackParamList>;
type Route = RouteProp<AreaStackParamList, 'CheckIn'>;

export function CheckInScreen() {
  const navigation = useNavigation<Nav>();
  const routeAgentId = useRoute<Route>().params?.agentId;
  const agentsQ = useQuery({ queryKey: ['area', 'agents'], queryFn: areaApi.agents });

  const [agentId, setAgentId] = useState(routeAgentId ?? '');
  const [note, setNote] = useState('');
  const [location, setLocation] = useState<DeviceLocation | null>(null);
  const [locating, setLocating] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<{ synced: boolean; message?: string } | null>(null);

  useEffect(() => {
    if (routeAgentId) setAgentId(routeAgentId);
  }, [routeAgentId]);

  const agents = agentsQ.data ?? [];
  const selected = agents.find((a) => a.id === agentId);

  const captureLocation = async () => {
    setLocating(true);
    setError(null);
    try {
      const loc = await requestForegroundLocation();
      setLocation(loc);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setLocating(false);
    }
  };

  const save = async () => {
    if (!agentId || !note.trim()) {
      setError('Select an agent and enter a visit note.');
      return;
    }
    if (!location) {
      setError('Capture location before saving.');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const local = await addVisit({
        agentId,
        agentCode: selected?.agentCode,
        agentName: selected?.businessName || selected?.fullName,
        note: note.trim(),
        latitude: location.latitude,
        longitude: location.longitude,
        simulated: location.simulated,
        synced: false,
      });

      try {
        const remote = await areaApi.createFieldVisit({
          agentId,
          notes: note.trim(),
          visitType: 'ROUTINE_AUDIT',
          latitude: location.latitude,
          longitude: location.longitude,
          locationSimulated: location.simulated,
          clientLocalId: local.id,
          visitedAt: local.createdAt,
        });
        await markVisitSynced(local.id, remote.id);
        setDone({ synced: true });
      } catch (syncErr) {
        const msg = errorMessage(syncErr);
        await markVisitSyncFailed(local.id, msg);
        setDone({
          synced: false,
          message: `Saved on this device, but backend sync failed: ${msg}`,
        });
      }
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSaving(false);
    }
  };

  if (done) {
    return (
      <Screen
        title={done.synced ? 'Check-in synced' : 'Check-in saved locally'}
        subtitle={done.synced ? 'Recorded on the server for your area' : 'Will remain on this device until sync succeeds'}>
        <StatusChip
          label={done.synced ? 'Synced to backend' : 'Not synced to backend'}
          tone={done.synced ? 'success' : 'warning'}
        />
        {done.message ? <Text style={styles.warn}>{done.message}</Text> : null}
        <Button
          label="Back to field visits"
          onPress={() => navigation.navigate('FieldVisits')}
          style={styles.cta}
        />
        <Button label="Another check-in" variant="secondary" onPress={() => navigation.replace('CheckIn', {})} />
      </Screen>
    );
  }

  return (
    <Screen title="Agent check-in" subtitle="Synced to the platform when you save">
      {agentsQ.isError ? <ErrorBanner message={errorMessage(agentsQ.error)} /> : null}
      {error ? <ErrorBanner message={error} /> : null}
      {agentsQ.isLoading ? <LoadingBlock label="Loading agents…" /> : null}

      <Card style={styles.card}>
        <Text style={styles.label}>Agent</Text>
        <View style={styles.agentList}>
          {agents.map((a) => (
            <Button
              key={a.id}
              label={`${a.agentCode} · ${a.businessName}`}
              variant={agentId === a.id ? 'primary' : 'secondary'}
              onPress={() => setAgentId(a.id)}
            />
          ))}
        </View>
      </Card>

      <TextField
        label="Visit note"
        value={note}
        onChangeText={setNote}
        placeholder="Purpose of visit, actions taken…"
        multiline
      />

      <Button
        label={location ? 'Refresh location' : 'Capture location'}
        variant="secondary"
        loading={locating}
        onPress={() => void captureLocation()}
        style={styles.cta}
      />
      {location ? (
        <View style={styles.locBox}>
          <Text style={styles.locText}>
            {location.latitude.toFixed(5)}, {location.longitude.toFixed(5)}
            {location.accuracy != null ? ` · ±${Math.round(location.accuracy)}m` : ''}
          </Text>
          {location.simulated ? <StatusChip label="Simulated GPS" tone="warning" /> : null}
        </View>
      ) : null}

      <Button label="Save check-in" onPress={() => void save()} loading={saving} disabled={!agentId || !note.trim()} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  card: { padding: spacing.lg, marginBottom: spacing.md, gap: spacing.sm },
  label: { ...typography.caption, color: colors.inkMuted },
  agentList: { gap: spacing.sm },
  cta: { marginVertical: spacing.md },
  locBox: { gap: spacing.sm, marginBottom: spacing.md },
  locText: { ...typography.body, color: colors.ink },
  warn: { ...typography.body, color: colors.warning, marginTop: spacing.md, marginBottom: spacing.sm },
});

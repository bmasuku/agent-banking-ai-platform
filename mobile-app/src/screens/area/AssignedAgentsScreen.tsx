import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { StyleSheet, View } from 'react-native';
import { areaApi } from '../../api/endpoints';
import { errorMessage } from '../../api/errors';
import {
  Card,
  EmptyState,
  ErrorBanner,
  ListRow,
  LoadingBlock,
  Screen,
  StatusChip,
} from '../../components';
import type { AreaStackParamList } from '../../navigation/types';
import { spacing } from '../../theme/tokens';
import { money } from '../../utils/money';

type Nav = NativeStackNavigationProp<AreaStackParamList>;

export function AssignedAgentsScreen() {
  const navigation = useNavigation<Nav>();
  const query = useQuery({
    queryKey: ['area', 'agents'],
    queryFn: areaApi.agents,
  });

  const agents = query.data ?? [];

  return (
    <Screen
      title="Assigned agents"
      subtitle="Tap an agent for financial profile"
      refreshing={query.isRefetching}
      onRefresh={() => query.refetch()}>
      {query.isError ? <ErrorBanner message={errorMessage(query.error)} /> : null}
      {query.isLoading ? <LoadingBlock label="Loading agents…" /> : null}
      {!query.isLoading && agents.length === 0 ? (
        <EmptyState title="No agents" message="No agents are assigned to your area." />
      ) : null}
      <View style={styles.list}>
        {agents.map((a) => (
          <Card key={a.id} style={styles.card}>
            <ListRow
              title={a.businessName || a.fullName}
              subtitle={`${a.agentCode} · ${a.tier} · ${a.status}`}
              value={money(a.efloat)}
              showChevron
              onPress={() => navigation.navigate('AgentProfile', { agentId: a.id })}
            />
            <View style={styles.chips}>
              {a.efloatLow ? <StatusChip label="Low e-float" tone="warning" /> : null}
              {a.cashLow ? <StatusChip label="Low cash" tone="warning" /> : null}
              <StatusChip label={a.riskLevel} tone={a.riskLevel === 'LOW' ? 'success' : 'warning'} />
            </View>
          </Card>
        ))}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  list: { gap: spacing.sm },
  card: { paddingHorizontal: spacing.md, paddingBottom: spacing.sm },
  chips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.sm,
    paddingBottom: spacing.sm,
  },
});
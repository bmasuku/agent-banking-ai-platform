import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
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
import { AgentSummary } from '../../types/api';
import { spacing } from '../../theme/tokens';
import { money } from '../../utils/money';

type Nav = NativeStackNavigationProp<AreaStackParamList>;
type FilterKey = 'all' | 'efloatLow' | 'cashLow' | 'active';

const FILTERS: { key: FilterKey; label: string }[] = [
  { key: 'all', label: 'All' },
  { key: 'active', label: 'Active' },
  { key: 'efloatLow', label: 'Low e-float' },
  { key: 'cashLow', label: 'Low cash' },
];

function applyFilter(agents: AgentSummary[], filter: FilterKey): AgentSummary[] {
  switch (filter) {
    case 'active':
      return agents.filter((a) => a.status === 'ACTIVE');
    case 'efloatLow':
      return agents.filter((a) => a.efloatLow);
    case 'cashLow':
      return agents.filter((a) => a.cashLow);
    default:
      return agents;
  }
}

export function AreaAgentsHubScreen() {
  const navigation = useNavigation<Nav>();
  const [filter, setFilter] = useState<FilterKey>('all');
  const query = useQuery({
    queryKey: ['area', 'agents'],
    queryFn: areaApi.agents,
  });

  const agents = query.data ?? [];
  const filtered = useMemo(() => applyFilter(agents, filter), [agents, filter]);
  const lowCount = agents.filter((a) => a.efloatLow).length;

  return (
    <Screen
      title="Agents"
      subtitle={`${agents.length} assigned · ${lowCount} low e-float`}
      refreshing={query.isRefetching}
      onRefresh={() => query.refetch()}>
      {query.isError ? <ErrorBanner message={errorMessage(query.error)} /> : null}
      {query.isLoading ? <LoadingBlock label="Loading agents…" /> : null}

      <View style={styles.filters}>
        {FILTERS.map((f) => (
          <Button
            key={f.key}
            label={f.label}
            variant={filter === f.key ? 'primary' : 'secondary'}
            onPress={() => setFilter(f.key)}
          />
        ))}
      </View>

      <Card style={styles.shortcut}>
        <ListRow
          title="Replenish float"
          subtitle="Open allocation workflow"
          showChevron
          onPress={() => navigation.navigate('Replenish', {})}
        />
        <ListRow
          title="Float predictions"
          subtitle="Agents needing attention"
          showChevron
          onPress={() => navigation.navigate('FloatPredictions')}
        />
      </Card>

      {!query.isLoading && filtered.length === 0 ? (
        <EmptyState title="No matches" message="No agents match this filter." />
      ) : null}

      <View style={styles.list}>
        {filtered.map((a) => (
          <Card key={a.id} style={styles.card}>
            <ListRow
              title={a.businessName || a.fullName}
              subtitle={`${a.agentCode} · Score ${a.creditScore}`}
              value={money(a.efloat)}
              showChevron
              onPress={() => navigation.navigate('AgentProfile', { agentId: a.id })}
            />
            <View style={styles.chips}>
              {a.efloatLow ? <StatusChip label="Low e-float" tone="warning" /> : null}
              {a.cashLow ? <StatusChip label="Low cash" tone="warning" /> : null}
              <StatusChip label={a.status} />
            </View>
          </Card>
        ))}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  filters: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.sm,
    marginBottom: spacing.md,
  },
  shortcut: { paddingHorizontal: spacing.md, marginBottom: spacing.sm },
  list: { gap: spacing.sm, marginTop: spacing.md },
  card: { paddingHorizontal: spacing.md, paddingBottom: spacing.sm },
  chips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.sm,
    paddingBottom: spacing.sm,
  },
});
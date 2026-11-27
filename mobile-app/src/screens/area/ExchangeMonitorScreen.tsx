import { useQuery } from '@tanstack/react-query';
import { format, isValid, parseISO } from 'date-fns';
import { StyleSheet, Text, View } from 'react-native';
import { areaApi } from '../../api/endpoints';
import { errorMessage } from '../../api/errors';
import {
  Card,
  EmptyState,
  ErrorBanner,
  ListRow,
  LoadingBlock,
  Screen,
  SectionHeader,
  StatusChip,
} from '../../components';
import { ExchangeResponse, OpenListingView, PageResponse } from '../../types/api';
import { colors, spacing, typography } from '../../theme/tokens';
import { money } from '../../utils/money';

function extractExchanges(data: unknown): ExchangeResponse[] {
  if (data && typeof data === 'object' && 'content' in data) {
    return (data as PageResponse<ExchangeResponse>).content ?? [];
  }
  if (Array.isArray(data)) return data as ExchangeResponse[];
  return [];
}

function formatWhen(value?: string | null): string {
  if (!value) return '—';
  const d = parseISO(value);
  if (!isValid(d)) return '—';
  return format(d, 'dd MMM yyyy HH:mm');
}

function statusTone(status: string) {
  const s = status?.toUpperCase?.() ?? '';
  if (s === 'COMPLETED' || s === 'ACCEPTED' || s === 'VERIFIED') return 'success' as const;
  if (s === 'PENDING' || s === 'PROPOSED' || s === 'OPEN' || s === 'MATCHED') return 'info' as const;
  if (s === 'EXPIRED' || s === 'CANCELLED' || s === 'REJECTED') return 'warning' as const;
  return 'info' as const;
}

function partyLabel(ex: ExchangeResponse): string {
  if (ex.identityRevealed && (ex.requesterAgentCode || ex.counterpartyAgentCode)) {
    return `${ex.requesterAgentCode ?? '—'} ↔ ${ex.counterpartyAgentCode ?? '—'}`;
  }
  return `${ex.anonymityCode ?? 'Anonymous'} · identities hidden`;
}

export function ExchangeMonitorScreen() {
  const boardQ = useQuery({
    queryKey: ['area', 'exchange-board'],
    queryFn: areaApi.exchangeBoard,
  });
  const exchangesQ = useQuery({
    queryKey: ['area', 'exchanges'],
    queryFn: areaApi.exchanges,
  });

  const board: OpenListingView[] = boardQ.data ?? [];
  const exchanges = extractExchanges(exchangesQ.data);
  const active = exchanges.filter((x) => {
    const s = (x.status || '').toUpperCase();
    return s !== 'OPEN'; // OPEN listings shown on anonymous board
  });

  return (
    <Screen
      title="Float exchanges"
      subtitle="Open requests stay anonymous until agents agree. Matched parties are visible for oversight."
      refreshing={boardQ.isRefetching || exchangesQ.isRefetching}
      onRefresh={() => {
        void boardQ.refetch();
        void exchangesQ.refetch();
      }}>
      {boardQ.isError ? <ErrorBanner message={errorMessage(boardQ.error)} /> : null}
      {exchangesQ.isError ? <ErrorBanner message={errorMessage(exchangesQ.error)} /> : null}

      <SectionHeader title="Anonymous open requests" />
      <Text style={styles.hint}>Amounts and codes only — agent names are not shown.</Text>
      {boardQ.isLoading ? <LoadingBlock label="Loading board…" /> : null}
      {!boardQ.isLoading && board.length === 0 ? (
        <EmptyState title="No open requests" message="When agents post float needs, they appear here without names." />
      ) : null}
      <View style={styles.list}>
        {board.map((item) => (
          <Card key={item.id} style={styles.card}>
            <ListRow
              title={`${item.anonymityCode} · needs ${item.seeking}`}
              subtitle={`${item.note || 'No note'}\n${formatWhen(item.requestedAt)}`}
              value={money(item.amount)}
            />
            <View style={styles.footer}>
              <StatusChip label="OPEN" tone="info" />
              <StatusChip label={`${item.interestCount} interest`} tone="info" />
            </View>
          </Card>
        ))}
      </View>

      <SectionHeader title="Matched & settled" />
      {exchangesQ.isLoading ? <LoadingBlock label="Loading exchanges…" /> : null}
      {!exchangesQ.isLoading && active.length === 0 ? (
        <EmptyState title="None yet" message="Matched and completed exchanges appear here with identities." />
      ) : null}
      <View style={styles.list}>
        {active.map((x) => {
          const when = formatWhen(x.requestedAt ?? x.createdAt);
          const direction = (x.direction ?? '').replace(/_/g, ' ') || '—';
          return (
            <Card key={x.id} style={styles.card}>
              <ListRow
                title={x.reference}
                subtitle={`${partyLabel(x)}\n${when}`}
                value={money(x.amount)}
              />
              <View style={styles.footer}>
                <StatusChip label={x.status || 'UNKNOWN'} tone={statusTone(x.status)} />
                <StatusChip label={direction} tone="info" />
                <StatusChip
                  label={x.identityRevealed ? 'Identities visible' : 'Anonymous'}
                  tone={x.identityRevealed ? 'success' : 'info'}
                />
              </View>
              {x.note ? <ListRow title="Note" subtitle={x.note} /> : null}
            </Card>
          );
        })}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  hint: { ...typography.caption, color: colors.inkMuted, marginBottom: spacing.md },
  list: { gap: spacing.sm, marginBottom: spacing.xl },
  card: { paddingHorizontal: spacing.md, paddingBottom: spacing.sm },
  footer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.sm,
    paddingBottom: spacing.sm,
  },
});

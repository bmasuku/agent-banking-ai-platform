import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { format, isValid, parseISO } from 'date-fns';
import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { agentApi } from '../../api/endpoints';
import { errorMessage } from '../../api/errors';
import {
  AmountInput,
  Button,
  ConfirmSheet,
  EmptyState,
  ErrorBanner,
  ListRow,
  LoadingBlock,
  Screen,
  SectionHeader,
  StatusChip,
  TextField,
  parseAmount,
} from '../../components';
import type { AgentStackParamList } from '../../navigation/types';
import { colors, spacing, typography } from '../../theme/tokens';
import { newIdempotencyKey } from '../../utils/idempotency';
import { money } from '../../utils/money';

type ExchangeDirection = 'EFLOAT_FOR_CASH' | 'CASH_FOR_EFLOAT';
type Nav = NativeStackNavigationProp<AgentStackParamList>;

function formatWhen(value?: string | null): string {
  if (!value) return '—';
  const d = parseISO(value);
  return isValid(d) ? format(d, 'dd MMM yyyy HH:mm') : '—';
}

export function FloatExchangeScreen() {
  const navigation = useNavigation<Nav>();
  const qc = useQueryClient();
  const exchangesQ = useQuery({ queryKey: ['agent', 'exchanges'], queryFn: () => agentApi.exchanges() });
  const boardQ = useQuery({ queryKey: ['agent', 'exchange-board'], queryFn: agentApi.exchangeBoard });

  const [direction, setDirection] = useState<ExchangeDirection>('CASH_FOR_EFLOAT');
  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const amountNum = parseAmount(amount);
  const mine = exchangesQ.data?.content ?? [];
  const board = boardQ.data ?? [];

  const openMut = useMutation({
    mutationFn: () =>
      agentApi.openExchangeListing(
        { direction, amount: amountNum, note: note.trim() || undefined },
        newIdempotencyKey(),
      ),
    onSuccess: () => {
      setConfirmOpen(false);
      setAmount('');
      setNote('');
      void qc.invalidateQueries({ queryKey: ['agent', 'exchanges'] });
      void qc.invalidateQueries({ queryKey: ['agent', 'exchange-board'] });
    },
    onError: (e) => setError(errorMessage(e)),
  });

  const interestMut = useMutation({
    mutationFn: (id: string) => agentApi.expressExchangeInterest(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['agent', 'exchange-board'] });
      void qc.invalidateQueries({ queryKey: ['agent', 'exchanges'] });
    },
    onError: (e) => setError(errorMessage(e)),
  });

  const seekingLabel =
    direction === 'CASH_FOR_EFLOAT'
      ? 'I need e-float (I will give cash)'
      : 'I need cash (I will give e-float)';

  return (
    <Screen
      title="Float exchange"
      subtitle="Post the amount you need anonymously. Names stay hidden until you both agree."
      refreshing={exchangesQ.isRefetching || boardQ.isRefetching}
      onRefresh={() => {
        void exchangesQ.refetch();
        void boardQ.refetch();
      }}>
      {error ? <ErrorBanner message={error} /> : null}

      <SectionHeader title="Request float" />
      <Text style={styles.hint}>Nearby agents and your area manager see the amount only — not your name.</Text>
      <View style={styles.dirRow}>
        {([
          ['CASH_FOR_EFLOAT', 'Need e-float'],
          ['EFLOAT_FOR_CASH', 'Need cash'],
        ] as const).map(([value, label]) => (
          <Pressable
            key={value}
            onPress={() => setDirection(value)}
            style={[styles.dirChip, direction === value && styles.dirChipOn]}>
            <Text style={[styles.dirText, direction === value && styles.dirTextOn]}>{label}</Text>
          </Pressable>
        ))}
      </View>
      <Text style={styles.meta}>{seekingLabel}</Text>
      <AmountInput label="Amount looking for" value={amount} onChangeValue={setAmount} />
      <TextField label="Public note (optional)" value={note} onChangeText={setNote} />
      <Button
        label="Publish anonymous request"
        onPress={() => {
          setError(null);
          if (amountNum <= 0) {
            setError('Enter the amount you need.');
            return;
          }
          setConfirmOpen(true);
        }}
        loading={openMut.isPending}
      />

      <SectionHeader title="Nearby open requests" />
      {boardQ.isLoading ? <LoadingBlock label="Loading board…" /> : null}
      {!boardQ.isLoading && board.length === 0 ? (
        <EmptyState title="No open requests" message="When nearby agents post needs, they appear here anonymously." />
      ) : null}
      <View style={styles.list}>
        {board.map((item) => (
          <View key={item.id} style={styles.card}>
            <ListRow
              title={`${item.anonymityCode} · needs ${item.seeking}`}
              subtitle={`${money(item.amount)}${item.distanceKm != null ? ` · ${item.distanceKm.toFixed(1)} km` : ''}\n${item.note || 'No note'} · ${formatWhen(item.requestedAt)}`}
              value={`${item.interestCount} interest`}
            />
            <Button
              label="Express interest (anonymous)"
              variant="secondary"
              loading={interestMut.isPending}
              onPress={() => interestMut.mutate(item.id)}
            />
          </View>
        ))}
      </View>

      <SectionHeader title="My exchanges" />
      {exchangesQ.isLoading ? <LoadingBlock label="Loading…" /> : null}
      {!exchangesQ.isLoading && mine.length === 0 ? (
        <EmptyState title="None yet" message="Your open requests and matched exchanges appear here." />
      ) : null}
      <View style={styles.list}>
        {mine.map((ex) => {
          const title = ex.identityRevealed
            ? `${ex.requesterAgentCode ?? 'You'} ↔ ${ex.counterpartyAgentCode ?? '—'}`
            : `${ex.anonymityCode ?? 'Anonymous'} · ${ex.status}`;
          return (
            <View key={ex.id} style={styles.card}>
              <ListRow
                title={title}
                subtitle={`${ex.reference} · ${money(ex.amount)} · ${formatWhen(ex.requestedAt)}`}
                value={ex.status}
              />
              <View style={styles.chips}>
                <StatusChip label={ex.identityRevealed ? 'Identity revealed' : 'Anonymous'} tone={ex.identityRevealed ? 'success' : 'info'} />
                {ex.myVerifyToken ? <StatusChip label={`Token ${ex.myVerifyToken}`} tone="warning" /> : null}
              </View>
              <Button
                label="Open exchange"
                variant="secondary"
                onPress={() => navigation.navigate('ExchangeDetail', { exchangeId: ex.id })}
              />
            </View>
          );
        })}
      </View>

      <ConfirmSheet
        visible={confirmOpen}
        title="Publish anonymous request?"
        message={`Request ${money(amountNum)} (${direction === 'CASH_FOR_EFLOAT' ? 'need e-float' : 'need cash'}). Your name stays hidden until you accept someone's interest.`}
        confirmLabel="Publish"
        onConfirm={() => openMut.mutate()}
        onCancel={() => setConfirmOpen(false)}
        loading={openMut.isPending}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  hint: { ...typography.caption, color: colors.inkMuted, marginBottom: spacing.md },
  meta: { ...typography.caption, color: colors.inkMuted, marginBottom: spacing.sm },
  dirRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm, marginBottom: spacing.sm },
  dirChip: {
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.line,
    borderRadius: 999,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    backgroundColor: colors.white,
  },
  dirChipOn: { backgroundColor: colors.mint, borderColor: colors.forest },
  dirText: { ...typography.caption, color: colors.inkMuted },
  dirTextOn: { color: colors.forest, fontWeight: '700' },
  list: { gap: spacing.md, marginBottom: spacing.xl },
  card: { gap: spacing.sm, backgroundColor: colors.white, borderRadius: 12, padding: spacing.md },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
});

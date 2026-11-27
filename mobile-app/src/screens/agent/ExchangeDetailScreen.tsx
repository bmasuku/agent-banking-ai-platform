import { RouteProp, useRoute } from '@react-navigation/native';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { format, isValid, parseISO } from 'date-fns';
import { useState } from 'react';
import { FlatList, StyleSheet, Text, View } from 'react-native';
import { agentApi } from '../../api/endpoints';
import { errorMessage } from '../../api/errors';
import {
  Button,
  Card,
  EmptyState,
  ErrorBanner,
  ListRow,
  LoadingBlock,
  Screen,
  SectionHeader,
  StatusChip,
  TextField,
} from '../../components';
import type { AgentStackParamList } from '../../navigation/types';
import { colors, spacing, typography } from '../../theme/tokens';
import { money } from '../../utils/money';

type Route = RouteProp<AgentStackParamList, 'ExchangeDetail'>;

function formatWhen(value?: string | null): string {
  if (!value) return '—';
  const d = parseISO(value);
  return isValid(d) ? format(d, 'dd MMM yyyy HH:mm') : '—';
}

export function ExchangeDetailScreen() {
  const { exchangeId } = useRoute<Route>().params;
  const qc = useQueryClient();
  const [partnerToken, setPartnerToken] = useState('');
  const [chatBody, setChatBody] = useState('');
  const [error, setError] = useState<string | null>(null);

  const exchangeQ = useQuery({
    queryKey: ['agent', 'exchange', exchangeId],
    queryFn: () => agentApi.getExchange(exchangeId),
  });
  const interestsQ = useQuery({
    queryKey: ['agent', 'exchange-interests', exchangeId],
    queryFn: () => agentApi.exchangeInterests(exchangeId),
    enabled: exchangeQ.data?.status === 'OPEN',
    retry: false,
  });
  const messagesQ = useQuery({
    queryKey: ['agent', 'exchange-messages', exchangeId],
    queryFn: () => agentApi.exchangeMessages(exchangeId),
    enabled: Boolean(exchangeQ.data?.chatEnabled),
    refetchInterval: exchangeQ.data?.chatEnabled ? 5000 : false,
  });

  const refresh = () => {
    void exchangeQ.refetch();
    void interestsQ.refetch();
    void messagesQ.refetch();
    void qc.invalidateQueries({ queryKey: ['agent', 'exchanges'] });
  };

  const matchMut = useMutation({
    mutationFn: (interestId: string) => agentApi.matchExchangeInterest(exchangeId, interestId),
    onSuccess: () => refresh(),
    onError: (e) => setError(errorMessage(e)),
  });
  const verifyMut = useMutation({
    mutationFn: () => agentApi.verifyExchange(exchangeId, partnerToken.trim()),
    onSuccess: () => {
      setPartnerToken('');
      refresh();
    },
    onError: (e) => setError(errorMessage(e)),
  });
  const completeMut = useMutation({
    mutationFn: () => agentApi.completeExchange(exchangeId),
    onSuccess: () => refresh(),
    onError: (e) => setError(errorMessage(e)),
  });
  const chatMut = useMutation({
    mutationFn: () => agentApi.postExchangeMessage(exchangeId, chatBody.trim()),
    onSuccess: () => {
      setChatBody('');
      void messagesQ.refetch();
    },
    onError: (e) => setError(errorMessage(e)),
  });
  const cancelMut = useMutation({
    mutationFn: () => agentApi.cancelExchange(exchangeId, `cancel-${Date.now()}`),
    onSuccess: () => refresh(),
    onError: (e) => setError(errorMessage(e)),
  });

  const ex = exchangeQ.data;
  if (exchangeQ.isLoading) {
    return (
      <Screen title="Exchange">
        <LoadingBlock label="Loading exchange…" />
      </Screen>
    );
  }
  if (!ex) {
    return (
      <Screen title="Exchange">
        <ErrorBanner message={errorMessage(exchangeQ.error) || 'Exchange not found'} />
      </Screen>
    );
  }

  const interests = interestsQ.data ?? [];
  const messages = messagesQ.data ?? [];

  return (
    <Screen
      title={ex.anonymityCode || ex.reference}
      subtitle={`${money(ex.amount)} · ${ex.status}`}
      refreshing={exchangeQ.isRefetching}
      onRefresh={refresh}>
      {error ? <ErrorBanner message={error} /> : null}

      <Card style={styles.card}>
        <Text style={styles.label}>Status</Text>
        <StatusChip label={ex.status} tone={ex.status === 'COMPLETED' ? 'success' : 'info'} />
        <Text style={styles.body}>
          {ex.identityRevealed
            ? `${ex.requesterBusinessName ?? ex.requesterAgentCode} ↔ ${ex.counterpartyBusinessName ?? ex.counterpartyAgentCode}`
            : 'Identities hidden until you accept an interested agent.'}
        </Text>
        <Text style={styles.meta}>Requested {formatWhen(ex.requestedAt)}</Text>
      </Card>

      {ex.status === 'OPEN' ? (
        <>
          <SectionHeader title="Anonymous interests" />
          {interestsQ.isLoading ? <LoadingBlock label="Loading interests…" /> : null}
          {!interestsQ.isLoading && interests.length === 0 ? (
            <EmptyState title="No interests yet" message="Nearby agents can express interest without seeing your name." />
          ) : null}
          {interests.map((i) => (
            <Card key={i.id} style={styles.card}>
              <ListRow
                title={i.anonymityCode}
                subtitle={`${i.note || 'No note'} · ${formatWhen(i.createdAt)}`}
                value={i.status}
              />
              {i.status === 'PENDING' ? (
                <Button
                  label="Agree & reveal identities"
                  onPress={() => matchMut.mutate(i.id)}
                  loading={matchMut.isPending}
                />
              ) : null}
            </Card>
          ))}
          <Button label="Cancel listing" variant="danger" onPress={() => cancelMut.mutate()} loading={cancelMut.isPending} />
        </>
      ) : null}

      {ex.identityRevealed ? (
        <>
          <SectionHeader title="Meetup verification" />
          <Card style={styles.card}>
            <Text style={styles.body}>
              Show your token to your partner in person. Enter theirs here to prove authenticity before transferring float.
            </Text>
            <Text style={styles.token}>Your token: {ex.myVerifyToken ?? '—'}</Text>
            <Text style={styles.meta}>
              You verified: {ex.requesterVerified ? 'yes' : 'no'} · Partner verified:{' '}
              {ex.counterpartyVerified ? 'yes' : 'no'}
            </Text>
            {ex.status === 'MATCHED' || ex.status === 'VERIFIED' ? (
              <>
                <TextField
                  label="Partner's token"
                  value={partnerToken}
                  onChangeText={setPartnerToken}
                  autoCapitalize="characters"
                />
                <Button
                  label="Verify partner token"
                  onPress={() => verifyMut.mutate()}
                  loading={verifyMut.isPending}
                  disabled={partnerToken.trim().length < 4}
                />
              </>
            ) : null}
            {ex.status === 'VERIFIED' ? (
              <Button
                label="Complete float transfer"
                onPress={() => completeMut.mutate()}
                loading={completeMut.isPending}
              />
            ) : null}
          </Card>
        </>
      ) : null}

      {ex.chatEnabled ? (
        <>
          <SectionHeader title="Private chat" />
          <FlatList
            data={messages}
            keyExtractor={(m) => m.id}
            scrollEnabled={false}
            ListEmptyComponent={<EmptyState title="No messages" message="Coordinate the meetup here." />}
            renderItem={({ item }) => (
              <View style={[styles.bubble, item.mine ? styles.mine : styles.theirs]}>
                <Text style={styles.bubbleMeta}>{item.senderLabel} · {formatWhen(item.createdAt)}</Text>
                <Text style={styles.bubbleBody}>{item.body}</Text>
              </View>
            )}
          />
          <TextField label="Message" value={chatBody} onChangeText={setChatBody} multiline />
          <Button
            label="Send"
            onPress={() => chatMut.mutate()}
            loading={chatMut.isPending}
            disabled={!chatBody.trim()}
          />
        </>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  card: { gap: spacing.sm, marginBottom: spacing.md },
  label: { ...typography.caption, color: colors.inkMuted },
  body: { ...typography.body, color: colors.ink, lineHeight: 22 },
  meta: { ...typography.caption, color: colors.inkFaint },
  token: { ...typography.subtitle, color: colors.forest, letterSpacing: 1 },
  bubble: { borderRadius: 12, padding: spacing.md, marginBottom: spacing.sm },
  mine: { backgroundColor: colors.mint, alignSelf: 'flex-end' },
  theirs: { backgroundColor: colors.white, alignSelf: 'flex-start', borderWidth: StyleSheet.hairlineWidth, borderColor: colors.line },
  bubbleMeta: { ...typography.micro, color: colors.inkFaint, marginBottom: 4 },
  bubbleBody: { ...typography.body, color: colors.ink },
});

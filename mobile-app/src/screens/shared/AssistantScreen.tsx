import { useEffect, useRef, useState } from 'react';
import {
  FlatList,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { sharedApi } from '../../api/endpoints';
import { errorMessage } from '../../api/errors';
import { AiBadge, Button, EmptyState, ErrorBanner, Screen, TextField } from '../../components';
import { useAuthStore } from '../../store/authStore';
import { colors, radii, spacing, typography } from '../../theme/tokens';

type Msg = { role: 'user' | 'assistant'; content: string };

const AGENT_PROMPTS = [
  'When will I run out of float?',
  'How much float should I request?',
  'What is my available commission?',
  'Am I eligible for an advance?',
];

const MANAGER_PROMPTS = [
  'Which agents are running low on float?',
  'Which agents should I visit?',
  'Which agents have excess float?',
  'How much float should I allocate today?',
];

export function AssistantScreen() {
  const role = useAuthStore((s) => s.user?.role);
  const prompts = role === 'AREA_MANAGER' ? MANAGER_PROMPTS : AGENT_PROMPTS;
  const [question, setQuestion] = useState('');
  const [messages, setMessages] = useState<Msg[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [disclaimer, setDisclaimer] = useState<string | null>(null);
  const listRef = useRef<FlatList<Msg>>(null);

  useEffect(() => {
    if (messages.length === 0) return;
    const t = setTimeout(() => listRef.current?.scrollToEnd({ animated: true }), 50);
    return () => clearTimeout(t);
  }, [messages, loading]);

  const send = async (text: string) => {
    const q = text.trim();
    if (!q || loading) return;
    setError(null);
    setLoading(true);
    setQuestion('');
    const next = [...messages, { role: 'user' as const, content: q }];
    setMessages(next);
    try {
      const res = await sharedApi.assistantChat(
        q,
        next.map((m) => ({ role: m.role, content: m.content })),
      );
      setMessages([...next, { role: 'assistant', content: res.answer }]);
      setDisclaimer(res.disclaimer ?? null);
    } catch (e) {
      setError(errorMessage(e));
      setMessages(messages);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Screen
      scroll={false}
      title="AI assistant"
      subtitle="Read-only · grounded in live balances via secured backend tools"
      padded
      edges={['top', 'left', 'right', 'bottom']}>
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        keyboardVerticalOffset={Platform.OS === 'ios' ? 8 : 0}>
        <View style={styles.banner}>
          <AiBadge label="AI assistant" />
          <Text style={styles.bannerText}>Cannot move money or approve advances.</Text>
        </View>
        {error ? <ErrorBanner message={error} /> : null}

        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          style={styles.promptScroll}
          contentContainerStyle={styles.promptRow}>
          {prompts.map((p) => (
            <Pressable
              key={p}
              disabled={loading}
              onPress={() => void send(p)}
              style={({ pressed }) => [styles.promptChip, pressed && styles.promptChipPressed, loading && styles.promptDisabled]}>
              <Text style={styles.promptChipText} numberOfLines={2}>
                {p}
              </Text>
            </Pressable>
          ))}
        </ScrollView>

        <View style={styles.chatPane}>
          <FlatList
            ref={listRef}
            data={messages}
            keyExtractor={(_, i) => String(i)}
            style={styles.list}
            contentContainerStyle={[styles.listContent, messages.length === 0 && styles.listEmpty]}
            keyboardShouldPersistTaps="handled"
            renderItem={({ item }) => (
              <View style={[styles.bubble, item.role === 'user' ? styles.userBubble : styles.botBubble]}>
                <View style={styles.bubbleHeader}>
                  <Text style={styles.bubbleRole}>{item.role === 'user' ? 'You' : 'Assistant'}</Text>
                  {item.role === 'assistant' ? <AiBadge /> : null}
                </View>
                <Text style={styles.bubbleText} selectable>
                  {item.content}
                </Text>
              </View>
            )}
            ListEmptyComponent={
              <EmptyState
                title="Ask a question"
                message="Tap a suggested prompt or type below. Replies appear in this conversation pane."
              />
            }
            ListFooterComponent={
              loading ? (
                <View style={[styles.bubble, styles.botBubble]}>
                  <Text style={styles.bubbleRole}>Assistant</Text>
                  <Text style={styles.thinking}>Thinking…</Text>
                </View>
              ) : undefined
            }
          />
        </View>

        {disclaimer ? <Text style={styles.disclaimer}>{disclaimer}</Text> : null}

        <View style={styles.composer}>
          <TextField
            label="Your question"
            value={question}
            onChangeText={setQuestion}
            placeholder="Ask about float, commissions, or advances…"
            multiline
            style={styles.composerInput}
          />
          <Button label="Send" onPress={() => void send(question)} loading={loading} disabled={!question.trim()} />
        </View>
      </KeyboardAvoidingView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  banner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    marginBottom: spacing.sm,
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.md,
    backgroundColor: colors.infoSoft,
    borderRadius: radii.md,
  },
  bannerText: { ...typography.caption, color: colors.info, flex: 1 },
  promptScroll: { flexGrow: 0, marginBottom: spacing.sm, maxHeight: 72 },
  promptRow: { gap: spacing.sm, paddingVertical: spacing.xs, paddingRight: spacing.md },
  promptChip: {
    maxWidth: 220,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    borderRadius: radii.md,
    backgroundColor: colors.mint,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.line,
    justifyContent: 'center',
  },
  promptChipPressed: { opacity: 0.85 },
  promptDisabled: { opacity: 0.5 },
  promptChipText: { ...typography.caption, color: colors.forest, fontWeight: '600' },
  chatPane: {
    flex: 1,
    minHeight: 180,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.line,
    borderRadius: radii.lg,
    backgroundColor: colors.sandDeep,
    overflow: 'hidden',
    marginBottom: spacing.sm,
  },
  list: { flex: 1 },
  listContent: { gap: spacing.sm, padding: spacing.md, flexGrow: 1 },
  listEmpty: { justifyContent: 'center' },
  bubble: {
    borderRadius: radii.md,
    padding: spacing.md,
    gap: spacing.xs,
  },
  userBubble: {
    alignSelf: 'flex-end',
    maxWidth: '92%',
    backgroundColor: colors.mint,
  },
  botBubble: {
    alignSelf: 'flex-start',
    maxWidth: '92%',
    backgroundColor: colors.white,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.line,
  },
  bubbleHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
  },
  bubbleRole: { ...typography.micro, color: colors.inkFaint, textTransform: 'uppercase' },
  bubbleText: { ...typography.body, color: colors.ink, lineHeight: 22 },
  thinking: { ...typography.body, color: colors.inkMuted, fontStyle: 'italic' },
  disclaimer: { ...typography.caption, color: colors.inkFaint, marginBottom: spacing.sm },
  composer: {
    gap: spacing.sm,
    paddingTop: spacing.sm,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: colors.line,
  },
  composerInput: { minHeight: 72, textAlignVertical: 'top' },
});

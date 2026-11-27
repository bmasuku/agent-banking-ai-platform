import { useState } from 'react';
import { KeyboardAvoidingView, Platform, StyleSheet, Text, View } from 'react-native';
import { DEMO_HINT } from '../../config';
import { errorMessage } from '../../api/errors';
import { BrandLogo, BrandMark, Button, ErrorBanner, Screen, TextField } from '../../components';
import { useAuthStore } from '../../store/authStore';
import { colors, radii, spacing, typography } from '../../theme/tokens';

export function LoginScreen() {
  const login = useAuthStore((s) => s.login);
  const [username, setUsername] = useState(DEMO_HINT.agent.username);
  const [password, setPassword] = useState(DEMO_HINT.agent.password);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const onSubmit = async () => {
    setError(null);
    setLoading(true);
    try {
      await login(username, password);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Screen scroll padded title={undefined} subtitle={undefined}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <View style={styles.brand}>
          <BrandMark size={56} />
          <BrandLogo height={40} />
          <Text style={styles.product}>Agent Banking</Text>
          <Text style={styles.lede}>
            Field operations for agents and area managers. Role is set by the server. Balances after login are live
            ledger data.
          </Text>
        </View>
        {error ? <ErrorBanner message={error} /> : null}
        <View style={styles.form}>
          <TextField
            label="Username"
            value={username}
            onChangeText={setUsername}
            autoCapitalize="none"
            autoCorrect={false}
            textContentType="username"
          />
          <TextField
            label="Password"
            value={password}
            onChangeText={setPassword}
            secureTextEntry
            textContentType="password"
          />
          <Button label="Sign in" onPress={onSubmit} loading={loading} />
        </View>
        <View style={styles.demo}>
          <Text style={styles.demoTitle}>Demo accounts</Text>
          <Text style={styles.demoLine}>
            Agent: {DEMO_HINT.agent.username} / {DEMO_HINT.agent.password}
          </Text>
          <Text style={styles.demoLine}>
            Area manager: {DEMO_HINT.manager.username} / {DEMO_HINT.manager.password}
          </Text>
          <View style={styles.demoActions}>
            <Button
              label="Fill agent"
              variant="secondary"
              onPress={() => {
                setUsername(DEMO_HINT.agent.username);
                setPassword(DEMO_HINT.agent.password);
              }}
            />
            <Button
              label="Fill manager"
              variant="secondary"
              onPress={() => {
                setUsername(DEMO_HINT.manager.username);
                setPassword(DEMO_HINT.manager.password);
              }}
            />
          </View>
        </View>
      </KeyboardAvoidingView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  brand: {
    alignItems: 'flex-start',
    gap: spacing.md,
    marginBottom: spacing.xl,
  },
  product: {
    ...typography.title,
    color: colors.ink,
  },
  lede: {
    ...typography.body,
    color: colors.inkMuted,
    lineHeight: 22,
  },
  form: {
    gap: spacing.md,
    marginBottom: spacing.xl,
  },
  demo: {
    backgroundColor: colors.mint,
    borderRadius: radii.lg,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.mintStrong,
    padding: spacing.lg,
    gap: spacing.sm,
  },
  demoTitle: {
    ...typography.subtitle,
    color: colors.forest,
  },
  demoLine: {
    ...typography.caption,
    color: colors.inkMuted,
  },
  demoActions: {
    gap: spacing.sm,
    marginTop: spacing.sm,
  },
});

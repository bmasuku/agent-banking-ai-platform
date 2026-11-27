import { StyleSheet, Text, View } from 'react-native';
import { API_BASE_URL } from '../../config';
import { Button, Card, Screen, StatusChip } from '../../components';
import { useAuthStore } from '../../store/authStore';
import { colors, spacing, typography } from '../../theme/tokens';

export function ProfileScreen() {
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);

  return (
    <Screen title="Profile" subtitle="Account details from the server. Role cannot be changed here.">
      <Card>
        <Text style={styles.name}>{user?.fullName ?? user?.username}</Text>
        <Text style={styles.meta}>@{user?.username}</Text>
        <View style={styles.chipRow}>
          <StatusChip label={user?.role ?? 'UNKNOWN'} tone="forest" />
        </View>
        <View style={styles.rows}>
          <Row label="Email" value={user?.email ?? '—'} />
          <Row label="Agent code" value={user?.agentCode ?? '—'} />
          <Row label="Area" value={user?.areaName ?? '—'} />
          <Row label="API" value={API_BASE_URL} />
        </View>
      </Card>
      <View style={styles.logout}>
        <Button label="Sign out" variant="danger" onPress={() => logout()} />
      </View>
    </Screen>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.row}>
      <Text style={styles.rowLabel}>{label}</Text>
      <Text style={styles.rowValue}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  name: { ...typography.title, color: colors.ink },
  meta: { ...typography.caption, color: colors.inkMuted, marginTop: spacing.xs },
  chipRow: { marginTop: spacing.md, marginBottom: spacing.lg },
  rows: { gap: spacing.md },
  row: { gap: spacing.xs },
  rowLabel: { ...typography.micro, color: colors.inkFaint, textTransform: 'uppercase' },
  rowValue: { ...typography.body, color: colors.ink },
  logout: { marginTop: spacing.xl },
});

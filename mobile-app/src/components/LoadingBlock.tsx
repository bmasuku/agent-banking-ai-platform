import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';
import { colors, spacing, typography } from '../theme/tokens';

type LoadingBlockProps = {
  label?: string;
  fullHeight?: boolean;
};

export function LoadingBlock({ label, fullHeight = false }: LoadingBlockProps) {
  return (
    <View
      style={[styles.wrap, fullHeight && styles.fullHeight]}
      accessibilityRole="progressbar"
      accessibilityLabel={label ?? 'Loading'}>
      <ActivityIndicator size="large" color={colors.forest} />
      {label ? <Text style={styles.label}>{label}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: spacing.xxl,
    gap: spacing.md,
  },
  fullHeight: {
    flex: 1,
  },
  label: {
    ...typography.body,
    color: colors.inkMuted,
  },
});
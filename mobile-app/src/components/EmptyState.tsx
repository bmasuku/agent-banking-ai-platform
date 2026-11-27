import { StyleSheet, Text, View } from 'react-native';
import { colors, radii, spacing, typography } from '../theme/tokens';
import { Button } from './Button';

type EmptyStateProps = {
  message: string;
  title?: string;
  actionLabel?: string;
  onAction?: () => void;
};

export function EmptyState({
  message,
  title = 'Nothing here yet',
  actionLabel,
  onAction,
}: EmptyStateProps) {
  return (
    <View style={styles.wrap} accessibilityRole="summary">
      <View style={styles.icon}>
        <Text style={styles.iconGlyph}>○</Text>
      </View>
      <Text style={styles.title}>{title}</Text>
      <Text style={styles.message}>{message}</Text>
      {actionLabel && onAction ? (
        <View style={styles.action}>
          <Button label={actionLabel} onPress={onAction} variant="secondary" />
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: spacing.xxl,
    paddingHorizontal: spacing.xl,
    gap: spacing.sm,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.line,
    borderStyle: 'dashed',
    borderRadius: radii.lg,
    backgroundColor: colors.white,
  },
  icon: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: colors.mint,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.xs,
  },
  iconGlyph: {
    fontSize: 18,
    color: colors.forest,
  },
  title: {
    ...typography.subtitle,
    color: colors.ink,
    textAlign: 'center',
  },
  message: {
    ...typography.body,
    color: colors.inkMuted,
    textAlign: 'center',
    lineHeight: 22,
  },
  action: {
    marginTop: spacing.lg,
    alignSelf: 'stretch',
    maxWidth: 280,
  },
});

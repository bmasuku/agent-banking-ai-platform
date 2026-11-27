import { Pressable, StyleSheet, Text, View } from 'react-native';
import { colors, spacing, typography } from '../theme/tokens';

type ListRowProps = {
  title: string;
  subtitle?: string;
  value?: string;
  showChevron?: boolean;
  onPress?: () => void;
};

export function ListRow({
  title,
  subtitle,
  value,
  showChevron = false,
  onPress,
}: ListRowProps) {
  const content = (
    <>
      <View style={styles.main}>
        <Text style={styles.title} numberOfLines={1}>
          {title}
        </Text>
        {subtitle ? (
          <Text style={styles.subtitle} numberOfLines={2}>
            {subtitle}
          </Text>
        ) : null}
      </View>
      <View style={styles.trailing}>
        {value ? (
          <Text style={styles.value} numberOfLines={1}>
            {value}
          </Text>
        ) : null}
        {showChevron ? <Text style={styles.chevron}>›</Text> : null}
      </View>
    </>
  );

  if (onPress) {
    return (
      <Pressable
        onPress={onPress}
        style={({ pressed }) => [styles.row, pressed && styles.pressed]}
        accessibilityRole="button">
        {content}
      </Pressable>
    );
  }

  return <View style={styles.row}>{content}</View>;
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: 52,
    paddingVertical: spacing.md,
    gap: spacing.md,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.line,
  },
  pressed: {
    opacity: 0.85,
  },
  main: {
    flex: 1,
    gap: spacing.xs,
  },
  title: {
    ...typography.bodyStrong,
    color: colors.ink,
  },
  subtitle: {
    ...typography.caption,
    color: colors.inkMuted,
  },
  trailing: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    maxWidth: '40%',
  },
  value: {
    ...typography.bodyStrong,
    color: colors.forest,
  },
  chevron: {
    fontSize: 22,
    color: colors.inkFaint,
    lineHeight: 24,
  },
});
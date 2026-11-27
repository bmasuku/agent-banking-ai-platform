import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { colors, radii, spacing, typography } from '../theme/tokens';
import { Button } from './Button';

type ConfirmSheetProps = {
  visible: boolean;
  title: string;
  message?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  onConfirm: () => void;
  onCancel: () => void;
  loading?: boolean;
  destructive?: boolean;
};

export function ConfirmSheet({
  visible,
  title,
  message,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  onConfirm,
  onCancel,
  loading = false,
  destructive = false,
}: ConfirmSheetProps) {
  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onRequestClose={onCancel}
      statusBarTranslucent>
      <Pressable style={styles.backdrop} onPress={onCancel}>
        <Pressable style={styles.sheet} onPress={(e) => e.stopPropagation()}>
          <View style={[styles.accent, destructive ? styles.accentDanger : styles.accentPrimary]} />
          <Text style={styles.title}>{title}</Text>
          {message ? <Text style={styles.message}>{message}</Text> : null}
          <Text style={styles.hint}>
            {destructive
              ? 'This action cannot be undone. Confirm only if you are sure.'
              : 'Review the details carefully before confirming.'}
          </Text>
          <View style={styles.actions}>
            <Button
              label={confirmLabel}
              onPress={onConfirm}
              variant={destructive ? 'danger' : 'primary'}
              loading={loading}
            />
            <Button
              label={cancelLabel}
              onPress={onCancel}
              variant="ghost"
              disabled={loading}
            />
          </View>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(20, 32, 27, 0.45)',
    justifyContent: 'flex-end',
    padding: spacing.lg,
  },
  sheet: {
    backgroundColor: colors.white,
    borderRadius: radii.xl,
    padding: spacing.xl,
    gap: spacing.md,
    overflow: 'hidden',
  },
  accent: {
    height: 4,
    marginHorizontal: -spacing.xl,
    marginTop: -spacing.xl,
    marginBottom: spacing.sm,
  },
  accentPrimary: { backgroundColor: colors.forestSoft },
  accentDanger: { backgroundColor: colors.danger },
  title: {
    ...typography.title,
    color: colors.ink,
  },
  message: {
    ...typography.body,
    color: colors.inkMuted,
    lineHeight: 22,
  },
  hint: {
    ...typography.caption,
    color: colors.inkFaint,
  },
  actions: {
    marginTop: spacing.sm,
    gap: spacing.sm,
  },
});
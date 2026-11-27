import {
  ActivityIndicator,
  Pressable,
  StyleProp,
  StyleSheet,
  Text,
  ViewStyle,
} from 'react-native';
import { colors, radii, spacing, typography } from '../theme/tokens';

export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost';

type ButtonProps = {
  label: string;
  onPress?: () => void;
  variant?: ButtonVariant;
  loading?: boolean;
  disabled?: boolean;
  style?: StyleProp<ViewStyle>;
  fullWidth?: boolean;
};

const VARIANT_STYLES: Record<
  ButtonVariant,
  { container: ViewStyle; label: { color: string }; spinner: string }
> = {
  primary: {
    container: { backgroundColor: colors.forest },
    label: { color: colors.white },
    spinner: colors.white,
  },
  secondary: {
    container: {
      backgroundColor: colors.mint,
      borderWidth: 1,
      borderColor: colors.line,
    },
    label: { color: colors.forest },
    spinner: colors.forest,
  },
  danger: {
    container: { backgroundColor: colors.danger },
    label: { color: colors.white },
    spinner: colors.white,
  },
  ghost: {
    container: { backgroundColor: 'transparent' },
    label: { color: colors.forest },
    spinner: colors.forest,
  },
};

export function Button({
  label,
  onPress,
  variant = 'primary',
  loading = false,
  disabled = false,
  style,
  fullWidth = true,
}: ButtonProps) {
  const v = VARIANT_STYLES[variant];
  const isDisabled = disabled || loading;

  return (
    <Pressable
      onPress={onPress}
      disabled={isDisabled}
      accessibilityRole="button"
      accessibilityState={{ disabled: isDisabled, busy: loading }}
      style={({ pressed }) => [
        styles.base,
        v.container,
        fullWidth && styles.fullWidth,
        isDisabled && styles.disabled,
        pressed && !isDisabled && styles.pressed,
        style,
      ]}>
      {loading ? (
        <ActivityIndicator color={v.spinner} />
      ) : (
        <Text style={[styles.label, v.label]}>{label}</Text>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    minHeight: 48,
    paddingHorizontal: spacing.xl,
    paddingVertical: spacing.md,
    borderRadius: radii.md,
    alignItems: 'center',
    justifyContent: 'center',
  },
  fullWidth: {
    alignSelf: 'stretch',
  },
  label: {
    ...typography.bodyStrong,
  },
  disabled: {
    opacity: 0.5,
  },
  pressed: {
    opacity: 0.88,
  },
});
import {
  StyleProp,
  StyleSheet,
  Text,
  TextInput,
  TextInputProps,
  View,
  ViewStyle,
} from 'react-native';
import { colors, radii, spacing, typography } from '../theme/tokens';

type TextFieldProps = TextInputProps & {
  label?: string;
  error?: string;
  containerStyle?: StyleProp<ViewStyle>;
};

export function TextField({
  label,
  error,
  containerStyle,
  style,
  editable = true,
  ...rest
}: TextFieldProps) {
  const hasError = Boolean(error);

  return (
    <View style={[styles.wrap, containerStyle]}>
      {label ? <Text style={styles.label}>{label}</Text> : null}
      <TextInput
        {...rest}
        editable={editable}
        placeholderTextColor={colors.inkFaint}
        style={[
          styles.input,
          hasError && styles.inputError,
          !editable && styles.inputDisabled,
          style,
        ]}
        accessibilityLabel={label ?? rest.accessibilityLabel}
      />
      {hasError ? <Text style={styles.error}>{error}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    gap: spacing.sm,
  },
  label: {
    ...typography.caption,
    color: colors.inkMuted,
  },
  input: {
    ...typography.body,
    color: colors.ink,
    minHeight: 48,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
    backgroundColor: colors.white,
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: radii.md,
  },
  inputError: {
    borderColor: colors.danger,
  },
  inputDisabled: {
    backgroundColor: colors.sandDeep,
    color: colors.inkMuted,
  },
  error: {
    ...typography.caption,
    color: colors.danger,
  },
});
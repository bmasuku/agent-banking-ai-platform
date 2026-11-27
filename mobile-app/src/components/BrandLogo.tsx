import { Image, StyleSheet, View } from 'react-native';

type Props = {
  height?: number;
};

/** Official EcoCash wordmark from www.ecocash.co.zw */
export function BrandLogo({ height = 36 }: Props) {
  return (
    <Image
      source={require('../../assets/brand/ecocash-logo.png')}
      style={{ height, width: height * (225 / 76), resizeMode: 'contain' }}
      accessibilityLabel="EcoCash"
    />
  );
}

export function BrandMark({ size = 44 }: { size?: number }) {
  return (
    <View style={[styles.markWrap, { width: size, height: size, borderRadius: size * 0.22 }]}>
      <Image
        source={require('../../assets/brand/ecocash-app-icon.png')}
        style={{ width: size, height: size, borderRadius: size * 0.22 }}
        accessibilityIgnoresInvertColors
      />
    </View>
  );
}

const styles = StyleSheet.create({
  markWrap: {
    overflow: 'hidden',
  },
});

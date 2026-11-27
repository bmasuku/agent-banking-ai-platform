import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { ActivityIndicator, View } from 'react-native';
import { LoginScreen } from '../screens/auth/LoginScreen';
import { useAuthStore } from '../store/authStore';
import { colors } from '../theme/tokens';
import { AgentNavigator } from './AgentNavigator';
import { AreaNavigator } from './AreaNavigator';
import type { AuthStackParamList, RootStackParamList } from './types';

const Root = createNativeStackNavigator<RootStackParamList>();
const Auth = createNativeStackNavigator<AuthStackParamList>();

function AuthStack() {
  return (
    <Auth.Navigator screenOptions={{ headerShown: false }}>
      <Auth.Screen name="Login" component={LoginScreen} />
    </Auth.Navigator>
  );
}

export function RootNavigator() {
  const hydrated = useAuthStore((s) => s.hydrated);
  const accessToken = useAuthStore((s) => s.accessToken);
  const role = useAuthStore((s) => s.user?.role ?? null);

  if (!hydrated) {
    return (
      <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.sand }}>
        <ActivityIndicator color={colors.forest} size="large" />
      </View>
    );
  }

  const signedIn = Boolean(accessToken && role);
  const isAreaManager = role === 'AREA_MANAGER';
  const isAgent = role === 'AGENT';

  return (
    <NavigationContainer>
      <Root.Navigator screenOptions={{ headerShown: false }}>
        {!signedIn || (!isAreaManager && !isAgent) ? (
          <Root.Screen name="Auth" component={AuthStack} />
        ) : isAreaManager ? (
          <Root.Screen name="Area" component={AreaNavigator} />
        ) : (
          <Root.Screen name="Agent" component={AgentNavigator} />
        )}
      </Root.Navigator>
    </NavigationContainer>
  );
}

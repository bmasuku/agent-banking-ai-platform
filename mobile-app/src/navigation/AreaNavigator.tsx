import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { Text } from 'react-native';
import {
  AgentFinancialProfileScreen,
  AreaAgentsHubScreen,
  AreaDashboardScreen,
  AreaMapScreen,
  AreaMoreScreen,
  AreaPerformanceScreen,
  CheckInScreen,
  ExchangeMonitorScreen,
  FieldVisitsScreen,
  FloatPredictionsScreen,
  ReplenishScreen,
} from '../screens/area';
import { AssistantScreen } from '../screens/shared/AssistantScreen';
import { NotificationsScreen } from '../screens/shared/NotificationsScreen';
import { ProfileScreen } from '../screens/shared/ProfileScreen';
import { colors, typography } from '../theme/tokens';
import type { AreaStackParamList, AreaTabParamList } from './types';

const Tab = createBottomTabNavigator<AreaTabParamList>();
const Stack = createNativeStackNavigator<AreaStackParamList>();

function TabIcon({ label, color, size }: { label: string; color: string; size: number }) {
  return (
    <Text
      allowFontScaling={false}
      style={{
        fontSize: size,
        lineHeight: size,
        fontWeight: '600',
        color,
        textAlign: 'center',
      }}>
      {label}
    </Text>
  );
}

function AreaTabs() {
  return (
    <Tab.Navigator
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.forest,
        tabBarInactiveTintColor: colors.inkFaint,
        tabBarStyle: { backgroundColor: colors.white, borderTopColor: colors.line, paddingTop: 4 },
        tabBarLabelStyle: { ...typography.micro },
        tabBarIconStyle: { width: 36, height: 32 },
      }}>
      <Tab.Screen
        name="AreaHome"
        component={AreaDashboardScreen}
        options={{
          title: 'Home',
          tabBarIcon: ({ color, size }) => <TabIcon label="⌂" color={color} size={size} />,
        }}
      />
      <Tab.Screen
        name="AreaAgents"
        component={AreaAgentsHubScreen}
        options={{
          title: 'Agents',
          tabBarIcon: ({ color, size }) => <TabIcon label="☰" color={color} size={size} />,
        }}
      />
      <Tab.Screen
        name="AreaMap"
        component={AreaMapScreen}
        options={{
          title: 'Map',
          tabBarIcon: ({ color, size }) => <TabIcon label="◎" color={color} size={size} />,
        }}
      />
      <Tab.Screen
        name="AreaAssistant"
        component={AssistantScreen}
        options={{
          title: 'AI',
          tabBarIcon: ({ color, size }) => <TabIcon label="✦" color={color} size={size} />,
        }}
      />
      <Tab.Screen
        name="AreaMore"
        component={AreaMoreScreen}
        options={{
          title: 'More',
          tabBarIcon: ({ color, size }) => <TabIcon label="⋯" color={color} size={size} />,
        }}
      />
    </Tab.Navigator>
  );
}

export function AreaNavigator() {
  return (
    <Stack.Navigator
      screenOptions={{
        headerTintColor: colors.forest,
        headerStyle: { backgroundColor: colors.sand },
        headerTitleStyle: { ...typography.subtitle, color: colors.ink },
        contentStyle: { backgroundColor: colors.sand },
      }}>
      <Stack.Screen name="AreaTabs" component={AreaTabs} options={{ headerShown: false }} />
      <Stack.Screen name="AgentProfile" component={AgentFinancialProfileScreen} options={{ title: 'Agent profile' }} />
      <Stack.Screen name="FieldVisits" component={FieldVisitsScreen} options={{ title: 'Field visits' }} />
      <Stack.Screen name="CheckIn" component={CheckInScreen} options={{ title: 'Check in' }} />
      <Stack.Screen name="Replenish" component={ReplenishScreen} options={{ title: 'Float replenishment' }} />
      <Stack.Screen name="FloatPredictions" component={FloatPredictionsScreen} options={{ title: 'Float predictions' }} />
      <Stack.Screen name="ExchangeMonitor" component={ExchangeMonitorScreen} options={{ title: 'Exchange monitor' }} />
      <Stack.Screen name="AreaPerformance" component={AreaPerformanceScreen} options={{ title: 'Area performance' }} />
      <Stack.Screen name="Notifications" component={NotificationsScreen} options={{ title: 'Notifications' }} />
      <Stack.Screen name="Profile" component={ProfileScreen} options={{ title: 'Profile' }} />
    </Stack.Navigator>
  );
}

import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { Text } from 'react-native';
import {
  AdvanceHistoryScreen,
  AgentDashboardScreen,
  AgentMoreScreen,
  AgentOpsScreen,
  CashDepositScreen,
  CashInHandScreen,
  CommissionScreen,
  CreditScoreScreen,
  EfloatScreen,
  ExchangeDetailScreen,
  FloatExchangeScreen,
  NearbyAgentsScreen,
  RequestAdvanceScreen,
  TransactionConfirmScreen,
  TransactionHistoryScreen,
  WithdrawalScreen,
} from '../screens/agent';
import { AssistantScreen } from '../screens/shared/AssistantScreen';
import { NotificationsScreen } from '../screens/shared/NotificationsScreen';
import { ProfileScreen } from '../screens/shared/ProfileScreen';
import { colors, typography } from '../theme/tokens';
import type { AgentStackParamList, AgentTabParamList } from './types';

const Tab = createBottomTabNavigator<AgentTabParamList>();
const Stack = createNativeStackNavigator<AgentStackParamList>();

function TabIcon({ label, focused }: { label: string; focused: boolean }) {
  return (
    <Text style={{ fontSize: 11, fontWeight: focused ? '700' : '500', color: focused ? colors.forest : colors.inkFaint }}>
      {label}
    </Text>
  );
}

function AgentTabs() {
  return (
    <Tab.Navigator
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.forest,
        tabBarInactiveTintColor: colors.inkFaint,
        tabBarStyle: { backgroundColor: colors.white, borderTopColor: colors.line },
        tabBarLabelStyle: { ...typography.micro },
      }}>
      <Tab.Screen
        name="AgentHome"
        component={AgentDashboardScreen}
        options={{
          title: 'Home',
          tabBarIcon: ({ focused }) => <TabIcon label="⌂" focused={focused} />,
        }}
      />
      <Tab.Screen
        name="AgentOps"
        component={AgentOpsScreen}
        options={{
          title: 'Ops',
          tabBarIcon: ({ focused }) => <TabIcon label="⇄" focused={focused} />,
        }}
      />
      <Tab.Screen
        name="AgentHistory"
        component={TransactionHistoryScreen}
        options={{
          title: 'History',
          tabBarIcon: ({ focused }) => <TabIcon label="☰" focused={focused} />,
        }}
      />
      <Tab.Screen
        name="AgentAssistant"
        component={AssistantScreen}
        options={{
          title: 'AI',
          tabBarIcon: ({ focused }) => <TabIcon label="✦" focused={focused} />,
        }}
      />
      <Tab.Screen
        name="AgentMore"
        component={AgentMoreScreen}
        options={{
          title: 'More',
          tabBarIcon: ({ focused }) => <TabIcon label="⋯" focused={focused} />,
        }}
      />
    </Tab.Navigator>
  );
}

export function AgentNavigator() {
  return (
    <Stack.Navigator
      screenOptions={{
        headerTintColor: colors.forest,
        headerStyle: { backgroundColor: colors.sand },
        headerTitleStyle: { ...typography.subtitle, color: colors.ink },
        contentStyle: { backgroundColor: colors.sand },
      }}>
      <Stack.Screen name="AgentTabs" component={AgentTabs} options={{ headerShown: false }} />
      <Stack.Screen name="CashDeposit" component={CashDepositScreen} options={{ title: 'Cash in' }} />
      <Stack.Screen name="Withdrawal" component={WithdrawalScreen} options={{ title: 'Cash out' }} />
      <Stack.Screen name="TransactionConfirm" component={TransactionConfirmScreen} options={{ title: 'Confirmation' }} />
      <Stack.Screen name="Efloat" component={EfloatScreen} options={{ title: 'E-float' }} />
      <Stack.Screen name="CashInHand" component={CashInHandScreen} options={{ title: 'Cash in hand' }} />
      <Stack.Screen name="RequestAdvance" component={RequestAdvanceScreen} options={{ title: 'Request advance' }} />
      <Stack.Screen name="NearbyAgents" component={NearbyAgentsScreen} options={{ title: 'Nearby agents' }} />
      <Stack.Screen name="FloatExchange" component={FloatExchangeScreen} options={{ title: 'Float exchange' }} />
      <Stack.Screen name="ExchangeDetail" component={ExchangeDetailScreen} options={{ title: 'Exchange' }} />
      <Stack.Screen name="Commission" component={CommissionScreen} options={{ title: 'Commission' }} />
      <Stack.Screen name="CreditScore" component={CreditScoreScreen} options={{ title: 'Credit score' }} />
      <Stack.Screen name="AdvanceHistory" component={AdvanceHistoryScreen} options={{ title: 'Advances' }} />
      <Stack.Screen name="Notifications" component={NotificationsScreen} options={{ title: 'Notifications' }} />
      <Stack.Screen name="Profile" component={ProfileScreen} options={{ title: 'Profile' }} />
    </Stack.Navigator>
  );
}

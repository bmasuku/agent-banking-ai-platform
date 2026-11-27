import * as Location from 'expo-location';

/** Demo coordinates (Harare CBD) used when permission is denied or unavailable. */
export const SIMULATED_COORDS = {
  latitude: -17.8292,
  longitude: 31.0522,
};

export type DeviceLocation = {
  latitude: number;
  longitude: number;
  accuracy: number | null;
  simulated: boolean;
};

let lastConsentAt: number | null = null;

export async function requestForegroundLocation(): Promise<DeviceLocation> {
  const { status } = await Location.requestForegroundPermissionsAsync();
  if (status !== 'granted') {
    return { ...SIMULATED_COORDS, accuracy: null, simulated: true };
  }
  lastConsentAt = Date.now();
  try {
    const pos = await Location.getCurrentPositionAsync({
      accuracy: Location.Accuracy.Balanced,
    });
    return {
      latitude: pos.coords.latitude,
      longitude: pos.coords.longitude,
      accuracy: pos.coords.accuracy,
      simulated: false,
    };
  } catch {
    return { ...SIMULATED_COORDS, accuracy: null, simulated: true };
  }
}

export function getLastConsentAt() {
  return lastConsentAt;
}

/** Haversine distance in kilometres. */
export function distanceKm(
  a: { latitude: number; longitude: number },
  b: { latitude: number; longitude: number },
): number {
  const toRad = (d: number) => (d * Math.PI) / 180;
  const R = 6371;
  const dLat = toRad(b.latitude - a.latitude);
  const dLon = toRad(b.longitude - a.longitude);
  const lat1 = toRad(a.latitude);
  const lat2 = toRad(b.latitude);
  const h =
    Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

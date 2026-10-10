import 'package:flutter/services.dart';

class EscapeNativeBridge {
  static const MethodChannel _channel = MethodChannel('escape/native');

  Future<Map<String, dynamic>> getSavedWeekendPlaces() async {
    return await _channel.invokeMapMethod<String, dynamic>('getSavedWeekendPlaces') ??
        <String, dynamic>{};
  }

  Future<Map<String, dynamic>> getWeekendPlaces({bool refresh = false}) async {
    return await _channel.invokeMapMethod<String, dynamic>(
      'getWeekendPlaces', {'refresh': refresh},
    ) ?? <String, dynamic>{};
  }

  Future<void> openWeekendCyclingDirections(double latitude, double longitude) async {
    await _channel.invokeMethod('openWeekendCyclingDirections', {
      'latitude': latitude, 'longitude': longitude,
    });
  }

  Future<Map<String, dynamic>> getPermissionStatus() async {
    return await _channel.invokeMapMethod<String, dynamic>('getPermissionStatus') ??
        <String, dynamic>{};
  }

  Future<void> openUsageSettings() async {
    await _channel.invokeMethod('openUsageSettings');
  }

  Future<void> openOverlaySettings() async {
    await _channel.invokeMethod('openOverlaySettings');
  }

  Future<void> requestRuntimePermissions() async {
    await _channel.invokeMethod('requestRuntimePermissions');
  }

  Future<List<dynamic>> getInstalledSupportedApps() async {
    return await _channel.invokeMethod<List<dynamic>>('getInstalledSupportedApps') ??
        <dynamic>[];
  }

  Future<Map<String, dynamic>> getSettings() async {
    return await _channel.invokeMapMethod<String, dynamic>('getSettings') ??
        <String, dynamic>{};
  }

  Future<void> saveSettings(Map<String, dynamic> settings) async {
    await _channel.invokeMethod('saveSettings', settings);
  }

  Future<bool> startMonitor() async {
    return await _channel.invokeMethod<bool>('startMonitor') ?? false;
  }

  Future<void> stopMonitor() async {
    await _channel.invokeMethod('stopMonitor');
  }

  Future<void> triggerTestLock() async {
    await _channel.invokeMethod('triggerTestLock');
  }

  Future<Map<String, dynamic>> captureMissionPhoto() async {
    return await _channel.invokeMapMethod<String, dynamic>('captureMissionPhoto') ??
        <String, dynamic>{};
  }

  Future<Map<String, dynamic>> analyzeMissionPhoto() async {
    return await _channel.invokeMapMethod<String, dynamic>('analyzeMissionPhoto') ??
        <String, dynamic>{};
  }

  Future<void> discardMissionPhoto() async {
    await _channel.invokeMethod('discardMissionPhoto');
  }

  Future<bool> startCycleQuest() async {
    return await _channel.invokeMethod<bool>('startCycleQuest') ?? false;
  }

  Future<bool> speakMission() async {
    return await _channel.invokeMethod<bool>('speakMission') ?? false;
  }

  Future<void> startMission({String activity = 'walk'}) async {
    await _channel.invokeMethod('startMission', {'activity': activity});
  }

  Future<void> emergencyUnlock() async {
    await _channel.invokeMethod('emergencyUnlock');
  }

  Future<Map<String, dynamic>> getStatus() async {
    return await _channel.invokeMapMethod<String, dynamic>('getStatus') ??
        <String, dynamic>{};
  }

  Future<Map<String, dynamic>> getAiStatus() async {
    return await _channel.invokeMapMethod<String, dynamic>('getAiStatus') ??
        <String, dynamic>{};
  }

  Future<Map<String, dynamic>> ensureGemmaModel() async {
    return await _channel.invokeMapMethod<String, dynamic>('ensureGemmaModel') ??
        <String, dynamic>{};
  }

  Future<Map<String, dynamic>> retryGemmaModelDownload() async {
    return await _channel.invokeMapMethod<String, dynamic>(
          'retryGemmaModelDownload',
        ) ??
        <String, dynamic>{};
  }

  Future<void> openGemmaModelPage() async {
    await _channel.invokeMethod('openGemmaModelPage');
  }

  Future<Map<String, dynamic>> importGemmaModel() async {
    return await _channel.invokeMapMethod<String, dynamic>('importGemmaModel') ??
        <String, dynamic>{};
  }

  Future<Map<String, dynamic>> removeGemmaModel() async {
    return await _channel.invokeMapMethod<String, dynamic>('removeGemmaModel') ??
        <String, dynamic>{};
  }

  Future<Map<String, dynamic>> testGemmaMission() async {
    return await _channel.invokeMapMethod<String, dynamic>('testGemmaMission') ??
        <String, dynamic>{};
  }
}

class EscapeMission {
  final String title;
  final String instruction;

  const EscapeMission({
    required this.title,
    required this.instruction,
  });
}

abstract class MissionGenerator {
  Future<EscapeMission> generate({
    required String triggerApp,
    required int walkMinutes,
    required int minimumSteps,
  });
}

/// This fallback keeps ESCAPE usable before the local model is downloaded.
/// Later, LocalGemmaMissionGenerator will implement the same interface.
class FallbackMissionGenerator implements MissionGenerator {
  @override
  Future<EscapeMission> generate({
    required String triggerApp,
    required int walkMinutes,
    required int minimumSteps,
  }) async {
    return EscapeMission(
      title: 'Notice Something New',
      instruction:
          'Walk for $walkMinutes minutes and at least $minimumSteps steps. '
          'During the walk, notice three things you normally pass without '
          'paying attention to them.',
    );
  }
}

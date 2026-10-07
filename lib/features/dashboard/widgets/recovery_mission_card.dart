import 'package:flutter/material.dart';
import '../../../core/utils/duration_text.dart';
import '../../escape/escape_controller.dart';

class RecoveryMissionCard extends StatelessWidget {
  final EscapeController controller;

  const RecoveryMissionCard({
    super.key,
    required this.controller,
  });

  void _message(BuildContext context, String text) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(text)),
    );
  }

  String _localAiStatus(EscapeController controller) {
    switch (controller.aiDownloadState) {
      case 'manual-import-required':
        return 'Local Gemma needs one-time import';
      case 'failed':
        return 'Local Gemma unavailable — download failed';
      case 'paused':
        return 'Gemma download paused';
      case 'pending':
      case 'downloading':
        return 'Gemma model downloading';
      default:
        return 'Local Gemma not ready';
    }
  }

  @override
  Widget build(BuildContext context) {
    final evening = controller.lockMode == 'evening';
    final progress = controller.missionActive
        ? (controller.walkSeconds / controller.effectiveMissionSeconds)
            .clamp(0.0, 1.0)
        : 0.0;

    final aiGenerated = controller.missionSource == 'gemma-local';

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              evening ? '🌙 SCREEN-FREE MISSION' : '🌱 EARN YOUR SCROLL',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 10),
            Text(
              controller.missionTitle,
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 6),
            Text(controller.missionInstruction),
            const SizedBox(height: 10),
            Chip(
              avatar: Icon(
                controller.missionGenerating
                    ? Icons.hourglass_top
                    : aiGenerated
                        ? Icons.smart_toy
                        : Icons.eco_outlined,
                size: 18,
              ),
              label: Text(
                controller.missionGenerating
                    ? 'Gemma is preparing local missions…'
                    : aiGenerated
                        ? 'Generated locally by Gemma'
                        : controller.aiModelInstalled
                            ? 'Offline fallback mission'
                            : _localAiStatus(controller),
              ),
            ),
            const SizedBox(height: 14),
            if (!controller.missionActive) ...[
              Text(
                evening
                    ? 'Start this screen-free activity. After ${formatClock(controller.effectiveMissionSeconds)}, social apps unlock for ${controller.demoMode ? 2 : controller.accessMinutes} minutes.'
                    : 'Complete this mission first. Then social apps unlock for ${controller.demoMode ? 2 : controller.accessMinutes} minutes.',
              ),
              const SizedBox(height: 14),
              FilledButton.icon(
                onPressed: () async {
                  final message = await controller.startMission();
                  if (context.mounted) _message(context, message);
                },
                icon: const Icon(Icons.play_arrow),
                label: const Text('START MISSION'),
              ),
            ] else ...[
              LinearProgressIndicator(value: progress),
              const SizedBox(height: 8),
              Text(
                'Mission time: ${formatClock(controller.walkSeconds)} / '
                '${formatClock(controller.effectiveMissionSeconds)}',
              ),
              if (!evening) ...[
                const SizedBox(height: 4),
                Text(
                  controller.stepSensorAvailable
                      ? 'Steps: ${controller.walkSteps} / ${controller.effectiveMinSteps}'
                      : 'No step-counter sensor detected — time-only verification.',
                ),
              ],
              const SizedBox(height: 8),
              const Text('Put the phone away. ESCAPE will unlock social apps automatically when the mission is complete.'),
            ],
          ],
        ),
      ),
    );
  }
}

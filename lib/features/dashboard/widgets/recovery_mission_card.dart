import 'package:flutter/material.dart';
import '../../../core/utils/duration_text.dart';
import '../../escape/escape_controller.dart';

class RecoveryMissionCard extends StatelessWidget {
  final EscapeController controller;
  const RecoveryMissionCard({super.key, required this.controller});

  void _message(BuildContext context, String text) {
    if (!context.mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  @override
  Widget build(BuildContext context) {
    final evening = controller.lockMode == 'evening';
    final progress = controller.missionActive
        ? (controller.walkSeconds / controller.effectiveMissionSeconds).clamp(0.0, 1.0)
        : 0.0;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(evening ? '🌙 CREATE AN OFFLINE EVENING' : '🌱 PHOTO CHALLENGE',
                style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 10),
            Text(controller.missionTitle,
                style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 6),
            Text(controller.missionInstruction),
            const SizedBox(height: 10),
            Text(controller.missionSource == 'gemma-local'
                ? '🤖 Creative mission by local Gemma'
                : '🌱 Offline mission (Gemma unavailable or warming up)'),
            const SizedBox(height: 14),
            if (!evening) ...[
              Text('Photo target: ${controller.missionProofTag}. '
                  'Recognition runs entirely on your device. No images are uploaded.'),
              const SizedBox(height: 10),
              FilledButton.icon(
                onPressed: () async {
                  final result = await controller.submitMissionPhoto();
                  _message(context, result);
                },
                icon: const Icon(Icons.photo_camera_back_outlined),
                label: const Text('SUBMIT MISSION PHOTO'),
              ),
              const SizedBox(height: 6),
              const Text('The image checker recognises nature in the picture, '
                  'but cannot prove when or where it was taken.'),
            ] else if (!controller.missionActive) ...[
              Text('Do this screen-free activity for '
                  '${formatClock(controller.effectiveMissionSeconds)}. '
                  'Then earn ${controller.demoMode ? 2 : controller.accessMinutes} minutes of social access.'),
              const SizedBox(height: 12),
              FilledButton.icon(
                onPressed: () async {
                  final result = await controller.startMission();
                  _message(context, result);
                },
                icon: const Icon(Icons.self_improvement),
                label: const Text('BEGIN SCREEN-FREE ACTIVITY'),
              ),
            ] else ...[
              LinearProgressIndicator(value: progress),
              const SizedBox(height: 10),
              Text('${formatClock(controller.walkSeconds)} / '
                  '${formatClock(controller.effectiveMissionSeconds)}'),
              const SizedBox(height: 6),
              const Text('Leave your phone aside. Access unlocks when the timer finishes.'),
            ],
          ],
        ),
      ),
    );
  }
}

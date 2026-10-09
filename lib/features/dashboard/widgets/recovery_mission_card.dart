import 'package:flutter/material.dart';
import '../../escape/escape_controller.dart';

class RecoveryMissionCard extends StatefulWidget {
  final EscapeController controller;
  const RecoveryMissionCard({super.key, required this.controller});

  @override
  State<RecoveryMissionCard> createState() => _RecoveryMissionCardState();
}

class _RecoveryMissionCardState extends State<RecoveryMissionCard> {
  bool _busy = false;

  Future<void> _run(Future<String> Function() action) async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      final message = await action();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Action failed: $e')),
      );
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.controller;
    final night = c.lockMode == 'evening';
    final weekend = c.lockMode == 'weekend';
    final riding = c.missionActivity == 'cycle';
    final movementLabel = night ? 'Screen-free creating' :
        riding ? 'Bicycle ride' : 'Outdoor stroll';
    final minutes = (c.walkTargetSeconds / 60).ceil();
    final secondsRemaining = (c.walkTargetSeconds - c.walkSeconds).clamp(0, c.walkTargetSeconds);
    final moveGoal = night ? 'Write a real page on paper for $minutes minute(s)' :
        riding ? 'Cycle ${c.rideTargetMeters} m using GPS' :
        c.stepSensorAvailable ? 'Walk ${c.minRequiredSteps} steps in at least $minutes min'
          : 'Walk for at least $minutes min (no step sensor on this phone)';
    final moveDone = c.missionActive && secondsRemaining == 0 && (
        night || (riding ? c.rideMeters >= c.rideTargetMeters :
          !c.stepSensorAvailable || c.walkSteps >= c.minRequiredSteps));

    Widget clue(String number, String title, String subtitle, bool completed) =>
        Padding(
          padding: const EdgeInsets.symmetric(vertical: 9),
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            CircleAvatar(radius: 15, child: Text(number)),
            const SizedBox(width: 12),
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(title, style: Theme.of(context).textTheme.titleSmall),
              Text(subtitle),
            ])),
            Icon(completed ? Icons.check_circle : Icons.circle_outlined,
                color: completed ? Colors.green : null),
          ]),
        );

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Expanded(child: Text(night ? '🌙 AFTER-DARK CREATOR' :
              weekend ? '🚲 WEEKEND NATURE ADVENTURE' : '🌿 THE WORLD IS YOUR PASSWORD',
              style: Theme.of(context).textTheme.titleMedium)),
            IconButton(
              tooltip: 'Speak mission offline',
              icon: const Icon(Icons.volume_up_outlined),
              onPressed: () => _run(c.speakMission),
            ),
          ]),
          const SizedBox(height: 8),
          Text(c.missionTitle, style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 7),
          Text(c.missionInstruction),
          const Divider(height: 27),
          clue('1', movementLabel, moveGoal, moveDone),
          if (c.missionActive) ...[
            Text('Time: ${c.walkSeconds}s / ${c.walkTargetSeconds}s'),
            if (!night) Text(riding
                ? 'Ride: ${c.rideMeters} / ${c.rideTargetMeters} m'
                : 'Steps: ${c.walkSteps} / ${c.minRequiredSteps}'),
            const SizedBox(height: 8),
          ],
          clue('2', night ? 'Create nature on paper' : 'Find the real-world clue',
              night ? 'Write at least 20 readable words; include: ${c.missionProofCode}'
                    : 'Nature subject: ${c.missionProofTag}. Do not enter restricted or unsafe areas.',
              false),
          clue('3', 'Capture fresh proof',
              'Use the camera now; saved gallery images are not accepted. '
              'Local image/text recognition checks the result.', false),
          const SizedBox(height: 14),
          if (!c.missionActive)
            FilledButton.icon(
              onPressed: _busy ? null : () => _run(c.startMission),
              icon: Icon(night ? Icons.edit_note : Icons.directions_walk),
              label: Text(night ? 'BEGIN INDOOR QUEST' : 'START WALK QUEST'),
            ),
          if (!c.missionActive && weekend && !night) ...[
            const SizedBox(height: 8),
            OutlinedButton.icon(
              onPressed: _busy ? null : () => _run(c.startCycleQuest),
              icon: const Icon(Icons.directions_bike),
              label: const Text('START BICYCLE QUEST (GPS)'),
            ),
          ],
          if (c.missionActive) FilledButton.icon(
            onPressed: _busy || !c.proofReady ? null : () => _run(c.submitMissionPhoto),
            icon: const Icon(Icons.camera_alt_outlined),
            label: Text(_busy ? 'CHECKING PROOF…' : c.proofReady
                ? 'CAPTURE FRESH PHOTO & UNLOCK' : 'FINISH CLUE 1 TO UNLOCK CAMERA'),
          ),
          const SizedBox(height: 9),
          Text(night ? 'After dark: indoor proof only. No night cycling required.'
               : 'Keep the phone in your pocket while moving. Stop safely before taking a photo.',
               style: Theme.of(context).textTheme.bodySmall),
          const SizedBox(height: 6),
          Text(c.missionSource == 'gemma-local'
              ? 'Mission by offline Gemma · verification by on-device ML Kit'
              : 'Built-in offline quest · verification by on-device ML Kit',
              style: Theme.of(context).textTheme.bodySmall),
        ]),
      ),
    );
  }
}

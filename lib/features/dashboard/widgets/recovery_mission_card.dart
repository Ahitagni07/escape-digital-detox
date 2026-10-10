import 'dart:io';
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
  String? _photoPath;
  String? _photoMission;
  String? _photoFeedback;
  bool? _approved;

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

  Future<void> _capturePhoto() async {
    if (_busy) return;
    setState(() { _busy = true; _photoFeedback = null; _approved = null; });
    try {
      final response = await widget.controller.captureMissionPhoto();
      if (!mounted) return;
      setState(() {
        _photoPath = response['captured'] == true ? response['path']?.toString() : null;
        _photoMission = response['captured'] == true ? widget.controller.missionTitle : null;
        _photoFeedback = response['message']?.toString();
      });
    } catch (e) {
      if (mounted) setState(() => _photoFeedback = 'Camera error: $e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _analyzePhoto() async {
    if (_busy || _photoPath == null) return;
    setState(() { _busy = true; _photoFeedback = 'Analyzing image locally…'; _approved = null; });
    try {
      final response = await widget.controller.analyzeMissionPhoto();
      if (!mounted) return;
      final passed = response['approved'] == true;
      setState(() {
        _approved = passed;
        _photoFeedback = response['message']?.toString() ??
            (passed ? 'Mission approved!' : 'Photo did not match your mission.');
        if (passed) { _photoPath = null; _photoMission = null; }
      });
    } catch (e) {
      if (mounted) setState(() { _approved = false; _photoFeedback = 'Analysis failed: $e'; });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _discardPhoto() async {
    if (_busy) return;
    try { await widget.controller.discardMissionPhoto(); } catch (_) { }
    if (mounted) setState(() {
      _photoPath = null; _photoMission = null;
      _photoFeedback = null; _approved = null;
    });
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
          if (c.indoorChoiceAvailable && !c.missionActive) ...[
            const SizedBox(height: 6),
            const Text('Outdoor and indoor are both available after 18:00. '
                'The quest details update when you choose one.'),
          ],
          if (c.missionActive) ...[
            const SizedBox(height: 6),
            const Text('This quest stays active across 18:00. '
                'Its timer, steps and photo requirement will not reset.'),
          ],
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
          clue('3', 'Photograph and review',
              'Capture a new photo, check the preview, then tap Analyze photo. '
              'Everything stays on your phone.', _approved == true),
          const SizedBox(height: 14),
          if (!c.missionActive) ...[
            if (c.indoorChoiceAvailable) ...[
              Text('Choose one quest to earn social access:',
                  style: Theme.of(context).textTheme.titleSmall),
              const SizedBox(height: 8),
            ],
            FilledButton.icon(
              onPressed: _busy ? null : () => _run(c.startMission),
              icon: const Icon(Icons.directions_walk),
              label: Text(c.indoorChoiceAvailable ? 'START OUTDOOR QUEST' : 'START WALK QUEST'),
            ),
            if (c.indoorChoiceAvailable) ...[
              const SizedBox(height: 8),
              OutlinedButton.icon(
                onPressed: _busy ? null : () => _run(c.startIndoorQuest),
                icon: const Icon(Icons.edit_note),
                label: const Text('START INDOOR QUEST'),
              ),
            ],
          ],
          if (!c.missionActive && weekend && !c.indoorChoiceAvailable) ...[
            const SizedBox(height: 8),
            OutlinedButton.icon(
              onPressed: _busy ? null : () => _run(c.startCycleQuest),
              icon: const Icon(Icons.directions_bike),
              label: const Text('START BICYCLE QUEST (GPS)'),
            ),
          ],
          if (c.missionActive && _photoPath == null) ...[
            FilledButton.icon(
              onPressed: _busy || !c.proofReady ? null : _capturePhoto,
              icon: const Icon(Icons.camera_alt_outlined),
              label: Text(_busy ? 'OPENING CAMERA…' : c.proofReady
                  ? 'CAPTURE MISSION PHOTO' : 'FINISH CLUE 1 TO USE CAMERA'),
            ),
          ],
          if (c.missionActive && _photoPath != null && _photoMission == c.missionTitle) ...[
            const SizedBox(height: 12),
            Text('Review your photo', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            ClipRRect(
              borderRadius: BorderRadius.circular(12),
              child: Image.file(
                File(_photoPath!),
                height: 230,
                width: double.infinity,
                fit: BoxFit.cover,
                cacheWidth: 1200,
                errorBuilder: (_, error, stackTrace) => const SizedBox(
                  height: 120,
                  child: Center(child: Text('Preview unavailable — retake photo')),
                ),
              ),
            ),
            const SizedBox(height: 8),
            Text('Does your photo clearly show ${c.missionProofTag}?',
                style: Theme.of(context).textTheme.bodyMedium),
            const SizedBox(height: 10),
            Row(children: [
              Expanded(child: FilledButton.icon(
                onPressed: _busy ? null : _analyzePhoto,
                icon: const Icon(Icons.auto_awesome),
                label: Text(_busy ? 'ANALYZING…' : 'ANALYZE PHOTO'),
              )),
              const SizedBox(width: 8),
              OutlinedButton(
                onPressed: _busy ? null : _capturePhoto,
                child: const Text('RETAKE'),
              ),
            ]),
            TextButton.icon(
              onPressed: _busy ? null : _discardPhoto,
              icon: const Icon(Icons.delete_outline),
              label: const Text('Discard photo'),
            ),
          ],
          if (_photoFeedback != null) ...[
            const SizedBox(height: 8),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(12),
                color: _approved == true
                    ? Colors.green.withValues(alpha: 0.10)
                    : _approved == false
                        ? Colors.orange.withValues(alpha: 0.12)
                        : Theme.of(context).colorScheme.surfaceContainerHighest,
              ),
              child: Row(children: [
                Icon(_approved == true ? Icons.check_circle
                    : _approved == false ? Icons.info_outline : Icons.camera_alt_outlined,
                    color: _approved == true ? Colors.green : null),
                const SizedBox(width: 8),
                Expanded(child: Text(_photoFeedback!)),
              ]),
            ),
          ],
          const SizedBox(height: 9),
          Text(night ? 'Indoor proof: photograph your finished activity. Outdoor walking remains optional after 18:00.'
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

import 'dart:async';
import 'package:flutter/material.dart';
import '../../escape/escape_controller.dart';

class EmergencyUnlockCard extends StatefulWidget {
  final EscapeController controller;
  final ValueChanged<String> onUnlocked;

  const EmergencyUnlockCard({
    super.key,
    required this.controller,
    required this.onUnlocked,
  });

  @override
  State<EmergencyUnlockCard> createState() =>
      _EmergencyUnlockCardState();
}

class _EmergencyUnlockCardState extends State<EmergencyUnlockCard> {
  Timer? _unlockTimer;
  Timer? _progressTimer;
  bool _holding = false;
  double _progress = 0;

  void _startHold() {
    _unlockTimer?.cancel();
    _progressTimer?.cancel();

    final started = DateTime.now();

    setState(() {
      _holding = true;
      _progress = 0;
    });

    _progressTimer =
        Timer.periodic(const Duration(milliseconds: 100), (_) {
      if (!mounted || !_holding) return;

      final elapsed =
          DateTime.now().difference(started).inMilliseconds / 5000.0;

      setState(() {
        _progress = elapsed.clamp(0.0, 1.0);
      });
    });

    _unlockTimer = Timer(const Duration(seconds: 5), () async {
      _cancelHold();
      final message = await widget.controller.emergencyUnlock();
      widget.onUnlocked(message);
    });
  }

  void _cancelHold() {
    _unlockTimer?.cancel();
    _progressTimer?.cancel();

    if (!mounted) return;

    setState(() {
      _holding = false;
      _progress = 0;
    });
  }

  @override
  void dispose() {
    _unlockTimer?.cancel();
    _progressTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Emergency unlock',
          style: Theme.of(context).textTheme.titleMedium,
        ),
        const SizedBox(height: 6),
        const Text(
          'For genuinely urgent situations. Hold for 5 seconds. '
          'ESCAPE records only the local bypass count.',
        ),
        const SizedBox(height: 10),
        GestureDetector(
          onTapDown: (_) => _startHold(),
          onTapUp: (_) => _cancelHold(),
          onTapCancel: _cancelHold,
          child: Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  LinearProgressIndicator(
                    value: _holding ? _progress : 0,
                  ),
                  const SizedBox(height: 10),
                  Text(
                    _holding
                        ? 'Keep holding…'
                        : 'HOLD 5 SECONDS TO UNLOCK',
                  ),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }
}

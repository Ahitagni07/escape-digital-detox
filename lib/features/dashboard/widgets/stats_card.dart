import 'package:flutter/material.dart';
import '../../escape/escape_controller.dart';

class EscapeStatsCard extends StatelessWidget {
  final EscapeController controller;

  const EscapeStatsCard({
    super.key,
    required this.controller,
  });

  Widget _metric(BuildContext context, String value, String label) {
    return Expanded(
      child: Column(
        children: [
          Text(value, style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 3),
          Text(
            label,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodySmall,
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 18, horizontal: 10),
        child: Column(
          children: [
            Row(
              children: [
                _metric(context, '${controller.missionsCompleted}', 'Missions completed'),
                _metric(context, '${controller.walksCompleted}', 'Outdoor missions'),
                _metric(context, '${controller.streakDays}', 'Day streak'),
              ],
            ),
            const SizedBox(height: 18),
            Row(
              children: [
                _metric(context, '${controller.reclaimedMinutes}', 'Screen-free minutes'),
                _metric(context, '${controller.stepsEarned}', 'Steps earned'),
                _metric(context, '${controller.emergencyUnlocksToday}', 'Bypasses today'),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

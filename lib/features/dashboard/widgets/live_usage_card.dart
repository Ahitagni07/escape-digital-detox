import 'package:flutter/material.dart';
import '../../../core/utils/duration_text.dart';
import '../../escape/escape_controller.dart';

class LiveUsageCard extends StatelessWidget {
  final EscapeController controller;

  const LiveUsageCard({
    super.key,
    required this.controller,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.lock_open),
                const SizedBox(width: 8),
                Text(
                  'Social access earned',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ],
            ),
            const SizedBox(height: 10),
            Text(
              'Protected social apps are available for ${formatLongClock(controller.accessRemainingSeconds)}.',
            ),
            const SizedBox(height: 6),
            const Text(
              'When this window ends, ESCAPE locks them again and prepares the next offline mission.',
            ),
          ],
        ),
      ),
    );
  }
}

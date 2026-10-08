import 'package:flutter/material.dart';
import '../escape/escape_controller.dart';
import '../ai/ai_setup_screen.dart';
import 'widgets/ai_download_status_card.dart';
import 'widgets/live_usage_card.dart';
import 'widgets/protected_apps_section.dart';
import 'widgets/recovery_mission_card.dart';
import 'widgets/rules_card.dart';
import 'widgets/status_hero.dart';

class DashboardScreen extends StatelessWidget {
  final EscapeController controller;
  const DashboardScreen({super.key, required this.controller});

  void _message(BuildContext context, String value) {
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(value)));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('ESCAPE 🌱'),
        actions: [
          IconButton(
            tooltip: 'Offline Gemma setup',
            icon: const Icon(Icons.smart_toy_outlined),
            onPressed: () async {
              await Navigator.push(context,
                  MaterialPageRoute<void>(builder: (_) => const AiSetupScreen()));
              await controller.refreshStatus();
            },
          ),
          PopupMenuButton<String>(
            tooltip: 'More settings',
            onSelected: (value) async {
              if (value == 'setup') {
                controller.openSetup();
              } else if (value == 'pause') {
                final msg = await controller.stopEscape();
                _message(context, msg);
              } else if (value == 'demo') {
                final msg = await controller.triggerTestLock();
                _message(context, msg);
              } else if (value == 'emergency') {
                final msg = await controller.emergencyUnlock();
                _message(context, msg);
              }
            },
            itemBuilder: (_) => [
              const PopupMenuItem(value: 'setup', child: Text('Permissions / setup')),
              if (controller.serviceRunning && controller.demoMode)
                const PopupMenuItem(value: 'demo', child: Text('Reset demo mission')),
              if (controller.serviceRunning && controller.locked)
                const PopupMenuItem(value: 'emergency',
                    child: Text('Emergency access (short, limited)')),
              if (controller.serviceRunning && !controller.missionActive)
                const PopupMenuItem(value: 'pause', child: Text('Pause ESCAPE protection')),
            ],
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
        children: [
          StatusHero(controller: controller),
          const SizedBox(height: 12),
          if (!controller.aiModelInstalled) ...[
            AiDownloadStatusCard(controller: controller,
                onMessage: (message) => _message(context, message)),
            const SizedBox(height: 12),
          ],
          if (controller.locked) ...[
            RecoveryMissionCard(controller: controller),
            const SizedBox(height: 16),
          ] else if (controller.serviceRunning && controller.accessActive) ...[
            LiveUsageCard(controller: controller),
            const SizedBox(height: 16),
          ],
          ProtectedAppsSection(controller: controller),
          const SizedBox(height: 20),
          RulesCard(controller: controller),
          const SizedBox(height: 18),
          if (!controller.serviceRunning)
            FilledButton.icon(
              onPressed: () async {
                final result = await controller.startEscape();
                if (result != null) _message(context, result);
              },
              icon: const Icon(Icons.shield_outlined),
              label: const Text('ACTIVATE ESCAPE'),
            ),
        ],
      ),
    );
  }
}

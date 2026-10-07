import 'package:flutter/material.dart';
import '../../escape/escape_controller.dart';

class AiDownloadStatusCard extends StatelessWidget {
  final EscapeController controller;
  final ValueChanged<String> onMessage;

  const AiDownloadStatusCard({
    super.key,
    required this.controller,
    required this.onMessage,
  });

  String _bytesText(int value) {
    if (value <= 0) return '0 MB';
    return '${(value / 1000000).toStringAsFixed(1)} MB';
  }

  String get _title {
    if (controller.aiModelInstalled || controller.aiDownloadState == 'installed') {
      return 'Gemma ready offline';
    }

    switch (controller.aiDownloadState) {
      case 'manual-import-required':
        return 'Gemma model found outside ESCAPE';
      case 'downloading':
        return 'Downloading Gemma — ${controller.aiDownloadProgress}%';
      case 'pending':
        return 'Gemma download queued';
      case 'paused':
        return 'Gemma download paused';
      case 'failed':
        return 'Gemma download failed';
      case 'not-started':
        return 'Gemma download not started';
      default:
        return 'Checking local AI';
    }
  }

  IconData get _icon {
    if (controller.aiModelInstalled || controller.aiDownloadState == 'installed') {
      return Icons.smart_toy;
    }
    switch (controller.aiDownloadState) {
      case 'manual-import-required':
        return Icons.folder_open;
      case 'failed':
        return Icons.error_outline;
      case 'paused':
        return Icons.pause_circle_outline;
      default:
        return Icons.downloading;
    }
  }

  bool get _showProgress =>
      !controller.aiModelInstalled &&
      <String>{'pending', 'downloading', 'paused'}
          .contains(controller.aiDownloadState);

  bool get _showRetry =>
      !controller.aiModelInstalled &&
      controller.aiAutoDownloadConfigured &&
      <String>{'paused', 'failed', 'not-started'}
          .contains(controller.aiDownloadState);

  @override
  Widget build(BuildContext context) {
    final total = controller.aiDownloadTotalBytes > 0
        ? controller.aiDownloadTotalBytes
        : 584417280;
    final downloaded = controller.aiDownloadedBytes.clamp(0, total).toInt();
    final progress = total > 0 ? downloaded / total : 0.0;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(_icon),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    _title,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),

            if (_showProgress) ...[
              LinearProgressIndicator(
                value: controller.aiDownloadProgress <= 0 ? null : progress,
              ),
              const SizedBox(height: 8),
              Text(
                '${_bytesText(downloaded)} / ${_bytesText(total)} '
                '(${controller.aiDownloadProgress}%)',
              ),
              const SizedBox(height: 8),
            ],

            if (controller.aiDownloadMessage.isNotEmpty)
              Text(controller.aiDownloadMessage),

            if (controller.aiDownloadState == 'manual-import-required') ...[
              const SizedBox(height: 12),
              FilledButton.icon(
                onPressed: () async {
                  final message = await controller.importGemmaFromDownloads();
                  onMessage(message);
                },
                icon: const Icon(Icons.file_open),
                label: const Text('USE MODEL FROM DOWNLOADS'),
              ),
            ],

            if (controller.aiDownloadState == 'failed' &&
                controller.aiLastError.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text('Error: ${controller.aiLastError}'),
            ],

            if (controller.aiWifiOnly &&
                controller.aiAutoDownloadConfigured &&
                !controller.aiModelInstalled &&
                <String>{'pending', 'downloading', 'paused'}
                    .contains(controller.aiDownloadState)) ...[
              const SizedBox(height: 6),
              const Text('Download policy: Wi-Fi only'),
            ],

            if (_showRetry) ...[
              const SizedBox(height: 12),
              FilledButton.tonalIcon(
                onPressed: () async {
                  final message = await controller.resumeGemmaDownload();
                  onMessage(message);
                },
                icon: const Icon(Icons.refresh),
                label: Text(
                  controller.aiDownloadState == 'failed'
                      ? 'RETRY DOWNLOAD'
                      : 'RESUME / CHECK DOWNLOAD',
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

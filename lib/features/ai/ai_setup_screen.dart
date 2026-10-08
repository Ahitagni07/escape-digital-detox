import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../core/native/escape_native_bridge.dart';
import 'ai_model_status.dart';

class AiSetupScreen extends StatefulWidget {
  const AiSetupScreen({super.key});

  @override
  State<AiSetupScreen> createState() => _AiSetupScreenState();
}

class _AiSetupScreenState extends State<AiSetupScreen> {
  final EscapeNativeBridge _native = EscapeNativeBridge();

  AiModelStatus? _status;
  bool _busy = false;
  String? _testTitle;
  String? _testInstruction;
  Timer? _poller;

  @override
  void initState() {
    super.initState();
    _ensureAndRefresh();
    _poller = Timer.periodic(
      const Duration(seconds: 2),
      (_) => _refresh(),
    );
  }

  @override
  void dispose() {
    _poller?.cancel();
    super.dispose();
  }

  Future<void> _ensureAndRefresh() async {
    try {
      await _native.ensureGemmaModel();
    } catch (_) {}
    await _refresh();
  }

  Future<void> _refresh() async {
    try {
      final map = await _native.getAiStatus();
      if (!mounted) return;
      setState(() => _status = AiModelStatus.fromMap(map));
    } catch (e) {
      _message('Could not read AI status: $e');
    }
  }

  Future<void> _retryDownload() async {
    setState(() => _busy = true);
    try {
      await _native.retryGemmaModelDownload();
      await _refresh();
      _message(
        'Gemma download checked. Android keeps resumable paused progress.',
      );
    } on PlatformException catch (e) {
      _message(e.message ?? 'Could not restart the model download.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _importModel() async {
    setState(() => _busy = true);
    try {
      final result = await _native.importGemmaModel();
      await _refresh();
      if (result['cancelled'] == true) {
        _message('Model selection cancelled.');
      } else {
        _message('Gemma model imported. Offline AI is ready.');
      }
    } on PlatformException catch (e) {
      _message(e.message ?? 'Could not import the Gemma model.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _openModelPage() async {
    try {
      await _native.openGemmaModelPage();
    } on PlatformException catch (e) {
      _message(e.message ?? 'Could not open the model page.');
    }
  }

  Future<void> _testModel() async {
    setState(() {
      _busy = true;
      _testTitle = null;
      _testInstruction = null;
    });

    try {
      final result = await _native.testGemmaMission();

      if (!mounted) return;
      setState(() {
        _testTitle = result['title']?.toString();
        _testInstruction = result['instruction']?.toString();
      });

      await _refresh();
    } on PlatformException catch (e) {
      _message(e.message ?? 'Gemma test failed.');
      await _refresh();
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _message(String text) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(text)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final status = _status;

    return Scaffold(
      appBar: AppBar(title: const Text('Local AI setup')),
      body: status == null
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
              children: [
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(18),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Icon(
                              status.installed
                                  ? Icons.smart_toy
                                  : Icons.downloading,
                            ),
                            const SizedBox(width: 10),
                            Expanded(
                              child: Text(
                                status.installed
                                    ? 'Gemma is ready locally'
                                    : status.downloadText,
                                style: Theme.of(context).textTheme.titleMedium,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        Text('Model: ${status.modelFamily}'),
                        Text('Runtime: ${status.runtime}'),
                        Text('Backend: ${status.backend}'),
                        Text('Stored size: ${status.sizeText}'),

                        if (!status.installed &&
                            <String>{
                              'downloading',
                              'pending',
                              'paused',
                            }.contains(status.downloadState)) ...[
                          const SizedBox(height: 14),
                          LinearProgressIndicator(
                            value: status.downloadProgress <= 0
                                ? null
                                : status.downloadProgress / 100,
                          ),
                          const SizedBox(height: 6),
                          Text(
                            '${(status.downloadedBytes / 1000000).toStringAsFixed(1)} MB / '
                            '${(status.downloadTotalBytes / 1000000).toStringAsFixed(1)} MB '
                            '(${status.downloadProgress}%)',
                          ),
                          if (status.downloadMessage.isNotEmpty) ...[
                            const SizedBox(height: 6),
                            Text(status.downloadMessage),
                          ],
                        ],
                      ],
                    ),
                  ),
                ),

                const SizedBox(height: 14),

                if (!status.installed &&
                    status.downloadState == 'manual-import-required') ...[
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Use the model already in Downloads',
                            style: Theme.of(context).textTheme.titleMedium,
                          ),
                          const SizedBox(height: 8),
                          const Text(
                            'Android does not allow ESCAPE to silently open a '
                            'browser-downloaded file. Tap once below; the system '
                            'picker opens at Downloads. Select the .litertlm file '
                            'and ESCAPE copies it into private app storage.',
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 10),
                  FilledButton.icon(
                    onPressed: _busy ? null : _importModel,
                    icon: const Icon(Icons.file_open),
                    label: const Text('USE MODEL FROM DOWNLOADS'),
                  ),
                  const SizedBox(height: 8),
                  OutlinedButton.icon(
                    onPressed: _busy ? null : _openModelPage,
                    icon: const Icon(Icons.open_in_browser),
                    label: const Text('OPEN OFFICIAL MODEL PAGE'),
                  ),
                ],

                if (!status.installed &&
                    status.autoDownloadConfigured &&
                    status.downloadState == 'failed') ...[
                  const SizedBox(height: 10),
                  FilledButton.tonalIcon(
                    onPressed: _busy ? null : _retryDownload,
                    icon: const Icon(Icons.refresh),
                    label: const Text('RESUME / RETRY MODEL DOWNLOAD'),
                  ),
                ],

                if (status.installed) ...[
                  const SizedBox(height: 12),
                  FilledButton.tonalIcon(
                    onPressed:
                        _busy || status.escapeRunning ? null : _testModel,
                    icon: const Icon(Icons.science_outlined),
                    label: const Text('TEST LOCAL GEMMA'),
                  ),
                ],

                if (_busy) ...[
                  const SizedBox(height: 18),
                  const LinearProgressIndicator(),
                ],

                if (_testTitle != null && _testInstruction != null) ...[
                  const SizedBox(height: 20),
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(18),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text('🤖 Gemma test mission'),
                          const SizedBox(height: 8),
                          Text(
                            _testTitle!,
                            style: Theme.of(context).textTheme.titleLarge,
                          ),
                          const SizedBox(height: 8),
                          Text(_testInstruction!),
                        ],
                      ),
                    ),
                  ),
                ],

                if (status.lastError.isNotEmpty) ...[
                  const SizedBox(height: 16),
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(14),
                      child: Text('Last AI error: ${status.lastError}'),
                    ),
                  ),
                ],

                const SizedBox(height: 20),
                const Text(
                  'Privacy: after the model is present, mission generation runs locally on the phone.',
                ),
              ],
            ),
    );
  }
}

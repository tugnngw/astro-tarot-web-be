import 'dart:convert';
import 'package:flutter/foundation.dart';

// API client for Astro Tarot mobile app
class ApiClient {
  final String baseUrl;
  final String? accessToken;
  final String? refreshToken;

  ApiClient({
    required this.baseUrl,
    this.accessToken,
    this.refreshToken,
  });

  bool get isLiveBackend => baseUrl.isNotEmpty;

  bool get hasValidAccessToken {
    if (accessToken == null) return false;
    try {
      final parts = accessToken.split('.');
      if (parts.length != 3) return false;
      final payload = jsonDecode(base64UrlDecode(parts[1]));
      final exp = payload['exp'] as int;
      return exp * 1000 > DateTime.now().millisecondsSinceEpoch;
    } catch (_) {
      return false;
    }
  }

  Future<bool> refreshAccessToken() async {
    if (refreshToken == null) return false;

    try {
      final response = await http.post(
        Uri.parse('$baseUrl/auth/refresh'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({'refreshToken': refreshToken}),
      );

      if (response.statusCode == 401 || response.statusCode == 403) {
        return false;
      }

      if (response.statusCode != 200) {
        return false;
      }

      final data = jsonDecode(response.body) as Map<String, dynamic>;
      accessToken = data['accessToken'] as String;
      refreshToken = data['refreshToken'] as String;

      await _saveTokens();

      return true;
    } catch (e) {
      return false;
    }
  }

  Future<void> _saveTokens() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('access_token', accessToken ?? '');
    await prefs.setString('refresh_token', refreshToken ?? '');
  }

  Future<T> apiFetch<T>(String path, {Map<String, dynamic>? body, bool auth = true, bool retry = true}) async {
    if (!isLiveBackend) {
      throw Exception('Backend not available');
    }

    final headers = <String, String>{};
    if (auth && accessToken != null) {
      headers['Authorization'] = 'Bearer $accessToken';
    }

    final isFormData = body is Map<String, dynamic> && body.values.any((v) =>
      v is String || v is List || v is Map);

    final requestHeaders = Map<String, String>.from(headers);
    if (isFormData) {
      requestHeaders['Content-Type'] = 'application/json';
    }

    int maxRetries = 3;
    for (int attempt = 0; attempt < maxRetries; attempt++) {
      try {
        final response = await http.request(
          Uri.parse('$baseUrl$path'),
          method: body != null ? 'POST' : 'GET',
          headers: requestHeaders,
          body: body != null ? jsonEncode(body) : null,
        );

        if (response.statusCode >= 400) {
          final error = ApiError(
            response.statusCode,
            response.statusCode.toString(),
            response.reasonPhrase,
          );

          if (retry && _isRetryableError(response.statusCode)) {
            await Future.delayed(Duration(milliseconds: _getRetryDelay(attempt)));
            continue;
          }

          throw error;
        }

        final responseBody = await response.body;
        final data = jsonDecode(responseBody) as Map<String, dynamic>;
        return data as T;
    } catch (e) {
      if (attempt == maxRetries - 1) {
        throw e;
      }
      await Future.delayed(Duration(milliseconds: _getRetryDelay(attempt)));
    }
  }

  bool _isRetryableError(int statusCode) {
    return statusCode == 0 ||
           statusCode == 408 ||
           statusCode == 429 ||
           statusCode >= 500;
  }

  int _getRetryDelay(int attempt) {
    if (attempt >= _retryDelays.length) return NHIP_THU_LAI.last;
    return NHIP_THU_LAI[attempt];
  }

  static const List<int> NHIP_THU_LAI = [2000, 5000, 12000];

  bool _isRetryableError(int statusCode) {
    return statusCode == 0 ||
           statusCode == 408 ||
           statusCode == 429 ||
           statusCode >= 500;
  }
}

class ApiError {
  final int status;
  final String code;
  final String message;

  ApiError(this.status, this.code, this.message);

  @override
  String toString() => 'ApiError(status: $status, code: $code, message: $message)';
}

// Helper functions
String base64UrlDecode(String input) {
  return utf8.decode(base64UrlDecode(input));
}

Future<void> _saveTokens() async {
  final prefs = await SharedPreferences.getInstance();
  await prefs.setString('access_token', accessToken ?? '');
  await prefs.setString('refresh_token', refreshToken ?? '');
  await prefs.setString('base_url', baseUrl);
  await prefs.setString('expires_at', DateTime.now().millisecondsSinceEpoch.toString());
  await prefs.setString('last_refresh', DateTime.now().millisecondsSinceEpoch.toString());
  await prefs.setString('last_request', DateTime.now().millisecondsSinceEpoch.toString());
  await prefs.setString('last_response_code', '0');
}

class ApiError {
  final int status;
  final String code;
  final String message;

  ApiError(this.status, this.code, this.message);

  @override
  String toString() => 'ApiError(status: $status, code: $code, message: $message)';
}

Future<T> apiFetch<T>(String path, {Map<String, dynamic>? body, bool auth = true, bool retry = true}) async {
  if (!isLiveBackend) {
    throw Exception('Backend not available');
  }

  final headers = <String, String>{};
  if (auth && accessToken != null) {
    headers['Authorization'] = 'Bearer $accessToken';
  }

  final isFormData = body is Map<String, dynamic> && body.values.any((v) =>
    v is String || v is List || v is Map);

  final requestHeaders = Map<String, String>.from(headers);
  if (isFormData) {
    requestHeaders['Content-Type'] = 'application/json';
  }

  int maxRetries = 3;
  for (int attempt = 0; attempt < maxRetries; attempt++) {
    try {
      final response = await http.request(
        Uri.parse('$baseUrl$path'),
        method: body != null ? 'POST' : 'GET',
        headers: requestHeaders,
        body: body != null ? jsonEncode(body) : null,
      );

      if (response.statusCode >= 400) {
        final error = ApiError(
          response.statusCode,
          response.statusCode.toString(),
          response.reasonPhrase,
        );

        if (retry && _isRetryableError(response.statusCode)) {
          await Future.delayed(Duration(milliseconds: _getRetryDelay(attempt)));
          continue;
        }

        throw error;
      }

      final responseBody = await response.body;
      final data = jsonDecode(responseBody) as Map<String, dynamic>;
      return data as T;
    } catch (e) {
      if (attempt == maxRetries - 1) {
        throw e;
      }
      await Future.delayed(Duration(milliseconds: _getRetryDelay(attempt)));
    }
  }

  bool _isRetryableError(int statusCode) {
    return statusCode == 0 ||
           statusCode == 408 ||
           statusCode == 429 ||
           statusCode >= 500;
  }

  int _getRetryDelay(int attempt) {
    if (attempt >= _retryDelays.length) return NHIP_THU_LAI.last;
    return NHIP_THU_LAI[attempt];
  }

  static const List<int> NHIP_THU_LAI = [2000, 5000, 12000];

  bool _isRetryableError(int statusCode) {
    return statusCode == 0 ||
           statusCode == 408 ||
           statusCode == 429 ||
           statusCode >= 500;
  }
}

class ApiError {
  final int status;
  final String code;
  final String message;

  ApiError(this.status, this.code, this.message);

  @override
  String toString() => 'ApiError(status: $status, code: $code, message: $message)';
}
# KTHD Backend API Contract

This document specifies the expected HTTP endpoints, request/response JSON schemas, HTTP verbs, headers, and status codes for the KTHD Android application backend integration.

---

## Global Request Headers

All HTTP requests sent by the mobile app automatically include the following HTTP headers via OkHttp interceptor:

| Header Name | Type | Example | Description |
| :--- | :--- | :--- | :--- |
| `Content-Type` | `string` | `application/json` | Standard JSON payload format |
| `X-Device-ID` | `string` | `8f3a21b4c5e67890` | Participant's unique 64-bit Android ID |

---

## Endpoints Summary

| Endpoint Path | HTTP Verb | Purpose | Request Model | Response Model |
| :--- | :--- | :--- | :--- | :--- |
| `/context` | `POST` | Log app usage threshold events during an active task | `ContextPayload` | `ContextResponse` |
| `/consent` | `POST` | Record participant informed consent agreement | `ConsentPayload` | `ConsentResponse` |
| `/reward` | `POST` | Report reward observation data 5 minutes after intervention opportunity | `RewardPayload` | `RewardResponse` |

---

## Endpoint Specifications

### 1. Log Context Usage Event

Triggered periodically when a participant reaches the continuous app usage threshold (e.g. 60s) during an active task session.

* **HTTP Verb**: `POST`
* **Path**: `/context`

#### Request Body (`application/json`)
```json
{
  "id": 1,
  "task_id": 101,
  "device_id": "8f3a21b4c5e67890",
  "app_type": "Social",
  "continuous_duration": 60,
  "switch_count": 3,
  "timestamp": 1700000000000,
  "sync_status": "PENDING"
}
```

#### Request Schema
| Field | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `id` | `number` (Long) | Yes | Local database primary key ID |
| `task_id` | `number` (Long) | Yes | Identifier of the active task |
| `device_id` | `string` | Yes | Unique Participant Android ID |
| `app_type` | `string` | Yes | Name or category of foreground app (e.g. `"Social"`, `"Games"`) |
| `continuous_duration` | `number` (Long) | Yes | Continuous usage duration in seconds |
| `switch_count` | `number` (Int) | Yes | Total count of app switches during active task session |
| `timestamp` | `number` (Long) | Yes | Epoch timestamp in milliseconds when payload was recorded |
| `sync_status` | `string` | Yes | Local sync state (`"PENDING"`, `"SYNCED"`, or `"FAILED"`) |

#### Response Body (`200 OK`)
```json
{
  "notify": true
}
```

#### Response Schema
| Field | Type | Description |
| :--- | :--- | :--- |
| `notify` | `boolean` | If `true`, the mobile client triggers an alert notification to notify the participant. |

#### HTTP Status Codes
| Code | Reason | Description |
| :--- | :--- | :--- |
| `200 OK` | Success | Payload processed successfully. |
| `400 Bad Request` | Client Error | Malformed JSON or invalid data types. |
| `500 Internal Server Error` | Server Error | Backend server encountered an error processing the payload. |

---

### 2. Record Informed Consent

Triggered when the participant accepts the Informed Consent agreement upon first opening the app, or during background retries if offline.

* **HTTP Verb**: `POST`
* **Path**: `/consent`

#### Request Body (`application/json`)
```json
{
  "device_id": "8f3a21b4c5e67890",
  "consented": true,
  "consent_time_utc": "2026-10-21T12:00:00Z"
}
```

#### Request Schema
| Field | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `device_id` | `string` | Yes | Unique Participant Android ID |
| `consented` | `boolean` | Yes | Consent status (`true`) |
| `consent_time_utc` | `string` | Yes | Timestamp formatted in ISO 8601 UTC format |

#### Response Body (`200 OK`)
```json
{
  "success": true
}
```

#### Response Schema
| Field | Type | Description |
| :--- | :--- | :--- |
| `success` | `boolean` | Indicates successful registration of consent record. |

#### HTTP Status Codes
| Code | Reason | Description |
| :--- | :--- | :--- |
| `200 OK` / `201 Created` | Success | Consent record registered successfully. |
| `400 Bad Request` | Client Error | Missing required fields or invalid timestamp. |
| `500 Internal Server Error` | Server Error | Backend database or server error while persisting consent. |

---

### 3. Report Reward Information

Triggered automatically 5 minutes (300 seconds) after receiving a successful response from an intervention opportunity (`POST /context`).

* **HTTP Verb**: `POST`
* **Path**: `/reward`

#### Request Body (`application/json`)
```json
{
  "device_id": "8f3a21b4c5e67890",
  "context_id": 1,
  "task_id": 101,
  "screen_locked": true,
  "app_closed": true,
  "time_to_close": 45
}
```

#### Request Schema
| Field | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `device_id` | `string` | Yes | Unique Participant Android ID |
| `context_id` | `number` (Long) | Yes | Associated context payload ID |
| `task_id` | `number` (Long) | Yes | Associated task ID |
| `screen_locked` | `boolean` | Yes | `true` if screen was locked or turned off at any point during the 5-minute window |
| `app_closed` | `boolean` | Yes | `true` if target app was closed/backgrounded at any point during the 5-minute window |
| `time_to_close` | `number` (Int) | Yes | Seconds (0 to 300) it took to close the target app; defaults to 300 if not closed |

#### Response Body (`200 OK`)
```json
{
  "success": true
}
```

#### Response Schema
| Field | Type | Description |
| :--- | :--- | :--- |
| `success` | `boolean` | Indicates successful receipt of reward observation data. |

#### HTTP Status Codes
| Code | Reason | Description |
| :--- | :--- | :--- |
| `200 OK` | Success | Reward observation recorded successfully. |
| `400 Bad Request` | Client Error | Missing or invalid reward fields. |
| `500 Internal Server Error` | Server Error | Server error processing reward observation. |

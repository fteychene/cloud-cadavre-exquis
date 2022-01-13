{{- define "cadavre.fullname" -}}
{{-   if .Values.fullnameOverride -}}
{{-     .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{-   else -}}
{{-     .Release.Name | trunc 63 | trimSuffix "-" -}}
{{-   end -}}
{{- end -}}

{{- define "cadavre.register.fullname" -}}
{{-   printf "%s-register" (include "cadavre.fullname" . ) | trunc -63 -}}
{{- end -}}

{{- define "cadavre.subject.fullname" -}}
{{-   printf "%s-subject" (include "cadavre.fullname" . ) | trunc -63 -}}
{{- end -}}

{{- define "cadavre.adjective.fullname" -}}
{{-   printf "%s-adjective" (include "cadavre.fullname" . ) | trunc -63 -}}
{{- end -}}

{{- define "cadavre.verb.fullname" -}}
{{-   printf "%s-verb" (include "cadavre.fullname" . ) | trunc -63 -}}
{{- end -}}

{{- define "cadavre.dispatcher.fullname" -}}
{{-   printf "%s-dispatcher" (include "cadavre.fullname" . ) | trunc -63 -}}
{{- end -}}
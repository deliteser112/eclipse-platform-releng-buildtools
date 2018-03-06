// Copyright 2017 The Nomulus Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package google.registry.proxy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auto.value.AutoValue;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * JUL formatter that formats log messages in a single-line JSON that Stackdriver logging can parse.
 *
 * <p>There is no clear documentation on how to achieve this or on the format of the JSON. This is
 * much a trial and error process, plus a lot of searching. To summarize, if the logs are printed to
 * {@code STDOUT} or {@code STDERR} in a single-line JSON, with the content in the {@code message}
 * field and the log level in the {@code severity} field, it will be picked up by Stackdriver
 * logging agent running in GKE containers and logged at correct level..
 *
 * @see <a
 *     href="https://medium.com/retailmenot-engineering/formatting-python-logs-for-stackdriver-5a5ddd80761c">
 *     Formatting Python Logs from Stackdriver</a> <a
 *     href="https://stackoverflow.com/questions/44164730/gke-stackdriver-java-logback-logging-format">
 *     GKE & Stackdriver: Java logback logging format?</a>
 */
class GcpJsonFormatter extends Formatter {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public String format(LogRecord record) {
    try {
      return MAPPER.writeValueAsString(LogEvent.create(record)) + "\n";
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @AutoValue
  abstract static class LogEvent {

    /** Field that determines the log level. */
    @JsonProperty("severity")
    abstract String severity();

    /**
     * Field that stores the calling class and function when the log occurs.
     *
     * <p>This field is not used by Stackdriver, but it is useful and can be found when the log
     * entries are expanded
     */
    @JsonProperty("source")
    abstract String source();

    /** Field that contains the content, this will show up as the main entry in a log. */
    @JsonProperty("message")
    abstract String message();

    static LogEvent create(LogRecord record) {
      // Add an extra newline before the message. Stackdriver does not show newlines correctly, and
      // treats them as whitespace. If you want to see correctly formatted log message, expand the
      // log and look for the jsonPayload.message field. This newline makes sure that the entire
      // message starts on its own line, so that indentation within the message is correct.

      String message = "\n" + record.getMessage();
      Level level = record.getLevel();
      // See
      // https://github.com/GoogleCloudPlatform/google-cloud-java/blob/master/google-cloud-logging/src/main/java/com/google/cloud/logging/Severity.java
      // on how {@code Level} is mapped to severity.
      String severity;
      switch (level.intValue()) {
          // FINEST
        case 300:
          // FINER
        case 400:
          // FINE
        case 500:
          severity = "DEBUG";
          break;
          // CONFIG
        case 700:
          // INFO
        case 800:
          severity = "INFO";
          break;
          // WARNING
        case 900:
          severity = "WARNING";
          break;
          // SEVERE
        case 1000:
          severity = "ERROR";
          break;
        default:
          severity = "DEFAULT";
      }

      // The rest is mostly lifted from java.util.logging.SimpleFormatter.
      String stacktrace = "";
      if (record.getThrown() != null) {
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
          pw.println();
          record.getThrown().printStackTrace(pw);
        }
        stacktrace = sw.toString();
      }

      String source;
      if (record.getSourceClassName() != null) {
        source = record.getSourceClassName();
        if (record.getSourceMethodName() != null) {
          source += " " + record.getSourceMethodName();
        }
      } else {
        source = record.getLoggerName();
      }

      return new AutoValue_GcpJsonFormatter_LogEvent(severity, source, message + stacktrace);
    }
  }
}

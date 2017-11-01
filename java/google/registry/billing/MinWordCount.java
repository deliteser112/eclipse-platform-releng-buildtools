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

package google.registry.billing;

import org.apache.beam.sdk.transforms.DoFn;

/** A minimal word count, serving as proof of concept for the Beam pipeline. */
public class MinWordCount implements Runnable {

  public static void main(String[] args) {
    MinWordCount wordCount = new MinWordCount();
    wordCount.run();
  }

  @Override
  public void run() {
    // This is a stub function for a basic proof-of-concept Beam pipeline.
  }

  static class ExtractWordsFn extends DoFn<String, String> {
    @ProcessElement
    public void processElement(ProcessContext c) {
      for (String word : c.element().split("[^\\p{L}]+")) {
        if (!word.isEmpty()) {
          c.output(word);
        }
      }
    }
  }
}

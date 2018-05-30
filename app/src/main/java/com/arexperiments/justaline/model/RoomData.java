// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.arexperiments.justaline.model;

import com.google.android.gms.nearby.messages.Message;

/**
 * Created by Kat on 3/29/18.
 */

public class RoomData {

    public String key;

    private Long timestamp;

    private Message message;

    public RoomData(String key, Long timestamp) {
        this.key = key;
        this.timestamp = timestamp;

        String messageString = key + "," + timestamp.toString();
        message = new Message(messageString.getBytes());
    }

    public RoomData(Message message) {
        this.message = message;
        String messageString = new String(message.getContent());
        String[] parts = messageString.split(",");
        if (parts.length == 2) {
            try {
                key = parts[0];
                timestamp = Long.parseLong(parts[1]);
            } catch (RuntimeException e) {
                throw new MalformedDataException(
                        "Message does not meet format <int:code>,<long:timestamp>: "
                                + messageString);
            }
        } else {
            throw new MalformedDataException(
                    "Message does not meet format <code>,<timestamp>: " + messageString);
        }
    }

    public Message getMessage() {
        return message;
    }

    public String getKey() {
        return key;
    }

    public static class MalformedDataException extends RuntimeException {

        public MalformedDataException(String message) {
            super(message);
        }
    }
}

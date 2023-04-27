# th2-woodpecker-template v0.0.1

Template implementation of th2 load generator tool using th2-woodpecker library

## Configuration

Main configuration is done via setting following properties in a custom configuration:

+ **maxBatchSize** - maximum number of messages in a generated batch (`100` by default)
+ **maxOutputQueueSize** - maximum number of batches in output queue which wouldn't throttle message generation (`0` by default)

### Generator configuration

Generator can be configured via setting following properties in the `generatorSettings` block of the main configuration

+ **messageType** - output message type (`type` by default)
+ **protocol** - output message protocol (`protocol` by default)
+ **sessionAlias** - output message session alias (`session` by default)
+ **properties** - output message metadata properties (empty by default)
+ **fields** - output message fields (empty by default)

### Configuration example

```yaml
maxBatchSize: 1000
generatorSettings:
  sessionGroupPrefix: group
  sessionGroupNumber: 1
  sessionAliasPrefix: session
  sessionAliasNumber: 1
  protocol: protocol
  random:
    messageSize: 256
  oneOf:
    directionToExamples:
      FIRST:
        messages: 
        - "test-message-1"
        base64s:
        - "dGVzdC1tZXNzYWdlLTI="
      SECOND: 
        messages: 
        - "test-message-3"
        base64s:
        - "dGVzdC1tZXNzYWdlLTQ="
      }
    }
```

### MQ pins

* input queue with `subscribe` and `in` attributes for incoming messages
* output queue with `publish` and `out` attributes for generated messages

## Deployment via `infra-mgr`

Here's an example of `infra-mgr` config required to deploy this service

```yaml
apiVersion: th2.exactpro.com/v1
kind: Th2Box
metadata:
  name: woodpecker-template
spec:
  image-name: ghcr.io/th2-net/th2-woodpecker-template
  image-version: 0.0.1
  custom-config:
    maxBatchSize: 1000
    generatorSettings:
      sessionGroupPrefix: group
      sessionGroupNumber: 1
      sessionAliasPrefix: session
      sessionAliasNumber: 1
      protocol: protocol
      random:
        messageSize: 256
      oneOf:
        messages:
          - "8=FIXT.1.19=535=D10=111"
  type: th2-conn
  pins:
    - name: in_messages
      connection-type: mq
      attributes:
        - subscribe
        - in
        - protobuf-group
    - name: out_messages
      connection-type: mq
      attributes:
        - publish
        - out
        - protobuf-group
    - name: out_messages
      connection-type: mq
      attributes:
        - publish
        - out
        - transport-group
  extended-settings:
    service:
      enabled: false
```

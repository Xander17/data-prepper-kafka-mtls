### Original [Opensearch Data-Prepper](https://github.com/opensearch-project/data-prepper) sources with Kafka source mTLS support

**Pipeline config**:
```yaml
  source:
    kafka:
      bootstrap_servers: ["server:9092"]
      topics:
        - name: topic.name
          group_id: group-name
      encryption:
        type: ssl
        insecure: false
        trust_store_file_path: /path/to/truststore.jks
        trust_store_password: truststore-password 
        key_store_file_path: /path/to/keystore.jks
        key_store_password: keystore-password
        key_password: key-password
```

### Build
[How to build docker image](release/docker/README.md)  
Preferred to use release branch 2.11
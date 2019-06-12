
# customs-data-store

This repository is a permanent cache for customs related data.
It supports graphql queries both for querying and updating.

### GraphQL examples

For querying (SQL select):
```json
{ "query": "query { byInternalId( internalId: \"1111111\")  { notificationEmail { address, isValidated }  } }"}
{ "query": "query { byInternalId( internalId: \"1111111\") {internalId, eoriHistory {eori, validFrom,validUntil}, notificationEmail {address, isValidated}} }"}
```
For upserting (SQL insert and update):
```json
{"query" : "mutation {byInternalId(internalId:\"1111111\" notificationEmail:{isValidated:true} )}" }
{"query" : "mutation {byInternalId(internalId:\"1111111\" notificationEmail:{address:\"some.guy@company.uk\" isValidated:false}  )}" }
{"query" : "mutation {byInternalId(internalId:\"1111111\" eoriHistory:{eori:\"GB12345678\" validFrom:\"1987-03-20\" validUntil:\"1999-03-20\"}  notificationEmail:{address:\"rashmidth@rich-contractors.com\" isValidated:false}  )}" }
```



### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").

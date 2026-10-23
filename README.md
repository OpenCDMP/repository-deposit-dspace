# OpenCDMP DSpace deposit service

**repository-deposit-dspace** is an [OpenCDMP](https://github.com/OpenCDMP/OpenCDMP) deposit service that publishes plans to a [DSpace](https://dspace.org/) repository. It creates an item with the plan's metadata and files in a DSpace collection and returns the item's Handle, which OpenCDMP stores with the plan as its identifier.

The service implements the [repository-deposit-base](https://github.com/OpenCDMP/repository-deposit-base) contract. See [Deposit services](https://opencdmp.github.io/optional-services/deposit-services/) in the OpenCDMP documentation.

## Features

- Deposits a plan as a new DSpace item, or as a new version of an item deposited earlier
- Publishes under the organisation's account or the user's own account, with a DSpace email and password
- Uploads the plan document (PDF), the machine-actionable plan (RDA JSON) and the supporting files (ZIP)
- Publishes the item in the archive, submits it to the collection's review workflow, or leaves it as a draft, as the organisation chooses
- Lets users review and edit the item's metadata before it is submitted
- Fills DSpace metadata from the answers of the plan blueprint and asset templates, through [semantics](https://opencdmp.github.io/optional-services/deposit-services/dspace-semantics/)

## Requirements

- DSpace 8.x, and a DSpace account, with an email and password, that may submit items to the target collection
- An OpenCDMP 2.0 installation, and its Keycloak realm with the `plugins` client scope
- To build from source: Java 21 and Maven. The service is a Spring Boot 4.0 application built on `repository-deposit-base`; the exact versions are in [`pom.xml`](pom.xml).

## Run with Docker

The image is [`opencdmp/repository-deposit-dspace`](https://hub.docker.com/r/opencdmp/repository-deposit-dspace) on Docker Hub. It listens on port `8080`, reads its settings from `/config/app-<PROFILE>.env` and writes its logs to `/logs`:

```bash
docker run -d --name opencdmp-dspace \
  --network <opencdmp-network> \
  -e PROFILE=production \
  -v /srv/opencdmp/dspace/config:/config \
  -v /srv/opencdmp/dspace/logs:/logs \
  opencdmp/repository-deposit-dspace:<tag>
```

Use the tag that the [changelog](https://opencdmp.github.io/deployment/install/changelog/) lists for your OpenCDMP release. Keep the port on the internal network: the `/actuator` endpoints are served without authentication. For a Docker Compose entry, see [Registering plugin services](https://opencdmp.github.io/deployment/configuration/plugin-services/#step-1-run-the-service).

## Build from source

```bash
mvn clean package
docker build -t repository-deposit-dspace:local .
```

Maven builds `web/target/repository-deposit-web-1.0.0-SNAPSHOT.jar`, and the `Dockerfile` builds the service from source on its own. The service contacts Keycloak at `IDP_ISSUER_URI` when it starts, and does not start if it cannot reach it. To run the JAR against a local OpenCDMP installation, see [Run and test a plugin locally](https://opencdmp.github.io/developers/plugins/run-locally/).

## Configuration

The service reads its settings from environment variables or from `app-<PROFILE>.env`. The main ones:

| Variable | Description |
| --- | --- |
| `IDP_ISSUER_URI` | Keycloak realm URL, the same value as in the OpenCDMP API service |
| `IDP_CLAIMS_ROLES_PATH` | `plugins.roles` |
| `SECURITY_AUDIENCE` | `plugins` |
| `OPENCDMP_DOMAIN` | Address of the OpenCDMP installation, with a trailing slash |
| `DEPOSIT_DSPACE_URL` | DSpace REST API URL, such as `https://dspace.example.org/server/api/` |
| `DEPOSIT_DSPACE_RECORD_URL` | Address of a deposited item, with `{doi_id}` in place of its Handle |
| `DEPOSIT_DSPACE_USERNAME`, `DEPOSIT_DSPACE_PASSWORD` | Email and password of the installation's DSpace account |
| `DEPOSIT_DSPACE_COLLECTION_ID` | UUID of the DSpace collection that items are stored in |
| `DEPOSIT_DSPACE_TYPE` | Type of the deposited items (default `Dataset`) |
| `DEPOSIT_DSPACE_SUPPORTS_PREVIEW` | Metadata review before publishing (default `true`) |
| `STORAGE_PATH` | Folder of temporary and shared files |

All variables, with defaults and examples, are in [Deposit services: DSpace](https://opencdmp.github.io/deployment/configuration/backend/deposit/#dspace); the authentication, logging and storage settings are in [Settings common to all plugin services](https://opencdmp.github.io/deployment/configuration/plugin-services/#settings-common-to-all-plugin-services). Each organisation can override the account and the record defaults in its [organisation settings](https://opencdmp.github.io/optional-services/deposit-services/#organisation-settings).

### Publishing accounts

For each deposit, the service signs in to DSpace with the first of these that is set:

1. The email and password the user saved in their [profile](https://opencdmp.github.io/using/profile-settings/#repository-accounts) or typed in the publish dialog (`authInfo.authFields`, codes `dspace-email` and `dspace-password`)
2. The organisation's **Username** and **Password** settings
3. `DEPOSIT_DSPACE_USERNAME` and `DEPOSIT_DSPACE_PASSWORD`

OpenCDMP decides which accounts a user may publish under, and never falls back to the organisation's or the installation's account when a user chose their own. See [Publishing accounts and files](https://opencdmp.github.io/optional-services/deposit-services/#publishing-accounts-and-files).

### The account a deposit used

A new version of an item is created by the account that signs in, and DSpace refuses it unless that account may version the item, which depends on the repository's authorisation and versioning settings. After a deposit under a user's own account, OpenCDMP calls `POST /api/deposit/account` with the same credentials. The service signs in as for a deposit and reads the account from `GET <DEPOSIT_DSPACE_URL>authn/status?embed=eperson`. It returns the EPerson's UUID and a name to show: the first and last name, otherwise the NetID, otherwise the email, with the email added when it differs. OpenCDMP keeps them with the plan's Handle and names that account when a later version is published. It never asks for the organisation's account, so that account's DSpace identity is not stored or shown to users.

## Register with OpenCDMP

Register the service for the whole installation in the configuration of the API service, or for one organisation under **Settings > Plugins > Add plugin**. Use the plugin id `dspace`, and register a PDF and an RDA JSON transformer with it. See [Registering plugin services](https://opencdmp.github.io/deployment/configuration/plugin-services/) and [Organisation plugins](https://opencdmp.github.io/administering/tenant-management/plugins/).

## API

All endpoints are under `/api/deposit`. They accept only a Bearer token from the installation's Keycloak realm that carries the `plugins` audience; the OpenCDMP API service obtains one for each call. See [Let the plugin accept the API service's tokens](https://opencdmp.github.io/deployment/configuration/plugin-services/#plugin-tokens).

| Endpoint | Description |
| --- | --- |
| `POST /api/deposit` | Deposit a plan and return the item's Handle |
| `POST /api/deposit/preview` | Return the metadata that would be deposited, as an editable form |
| `POST /api/deposit/account` | Return the DSpace account that a deposit's credentials belong to (see [The account a deposit used](#the-account-a-deposit-used)) |
| `GET /api/deposit/configuration` | Return the service's `DepositConfiguration` |
| `GET /api/deposit/logo` | Return the DSpace logo, Base64-encoded |

A deposit takes a `PlanDepositModel`. For a new version of an item deposited earlier, `previousDOI` holds the item's Handle:

```json
{
  "planModel": {
    "id": "<plan-id>",
    "label": "My research plan",
    "previousDOI": "<handle-of-the-earlier-version>"
  },
  "authInfo": {
    "authFields": [
      { "code": "dspace-email", "textValue": "<user-email>" },
      { "code": "dspace-password", "textValue": "<user-password>" }
    ]
  }
}
```

An item submitted to the review workflow or left as a draft is not in the archive and has no Handle yet; the service then returns the item's UUID.

The models are described in the [Deposit plugin guide](https://opencdmp.github.io/developers/plugins/deposit/#key-interfaces-and-classes) and [Common models](https://opencdmp.github.io/developers/plugins/common-models/).

## Deposit preview

The service reports `supportsPreview: true` in its configuration unless `DEPOSIT_DSPACE_SUPPORTS_PREVIEW` is `false`. OpenCDMP then lets users review and edit the item's metadata in a form before it is submitted to DSpace:

- `POST /api/deposit/preview` takes the same `PlanDepositModel` as a deposit, runs the same mapping and returns the form, with the mapped values filled in.
- `POST /api/deposit` applies the user's `editedFields` and `editedGroups` to the item before it is submitted.

The contract is described in [Deposit preview and metadata review](https://opencdmp.github.io/developers/plugins/deposit-preview/).

## Related resources

- [Deposit services overview](https://opencdmp.github.io/optional-services/deposit-services/)
- [DSpace semantics reference](https://opencdmp.github.io/optional-services/deposit-services/dspace-semantics/)
- [Deposit services configuration](https://opencdmp.github.io/deployment/configuration/backend/deposit/#dspace)
- [Registering plugin services](https://opencdmp.github.io/deployment/configuration/plugin-services/)
- [Deposit a plan](https://opencdmp.github.io/using/plans/deposit-a-plan/): the user guide
- [Deposit plugin guide](https://opencdmp.github.io/developers/plugins/deposit/): building your own deposit service

## Contributing

Report bugs and suggest changes in [GitHub Issues](https://github.com/OpenCDMP/repository-deposit-dspace/issues). See [Contributing](https://opencdmp.github.io/developers/contributing/) for how to propose a change.

## License

repository-deposit-dspace is licensed under the [European Union Public Licence (EUPL) v1.2](LICENSE).

## Contact

Questions: email opencdmp at cite.gr.

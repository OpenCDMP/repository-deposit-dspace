# Repository Deposit DSpace for OpenCDMP

**repository-deposit-dspace** is an implementation of the [repository-deposit-base](https://github.com/OpenCDMP/repository-deposit-base) package that enables the deposition of **OpenCDMP Plans** into the [DSpace](https://dspace.org/) repository, automatically minting a **Digital Object Identifier (DOI)** for each deposited plan.

## Overview

This service integrates with the OpenCDMP platform to provide deposit functionality for DSpace, a widely-used open-access research repository. Users can deposit their plans to DSpace, making them citeable, publicly available, and preservable with a permanent DOI.

**Supported operations:**
- ✅ Deposit plans to DSpace
- ✅ Automatic DOI minting
- ✅ System-based and user-based depositions

---

## Quick start

This service implements the following endpoints as per `DepositController`

### API endpoints

- `POST /deposit` - Deposit a plan to DSpace
- `GET /configuration` - Get repository configuration
- `GET /logo` - Get DSpace logo (base64)

### Example

- **Deposit with System Credentials (Email, Password)**
```bash
 # Uses a system-wide credenitals configured by the service when authInfo is null.
 # No user action is required.

 POST /deposit
  {
    "planModel": {...},
    "authInfo": null
  }
```

- **Deposit with User Credentials (from OpenCDMP profile settings)**
```bash
 # The user has stored their credentials in their OpenCDMP profile settings (see https://opencdmp.github.io/user-guide/profile-settings/#external-plugin-settings).
 # Email, Password credentials are persistent and remains valid until the user updates it in their profile.

 POST /deposit
  {
    "planModel": {...},
    "authInfo": {
        "authFields": [
            {
                "code": "dspace-email"
                "textValue": "email"
            },
            {
                "code": "dspace-password"
                "textValue": "password"
            }
        ]
    } 
  }
```


- **Deposit a new version of an existing plan**
```bash
 # Same as case 1 (system credentials).
 # previousDOI is mandatory to indicate that this is a new version of an existing deposit in this repository.

 POST /deposit
  {
    "planModel": {
        "id": "plan-uuid",
        "title": "My Research Plan",
        "description": "Plan content",
        "previousDOI": "doi"
        // more
    },
    "authInfo": null
  }
```
---

## Integration with OpenCDMP

To integrate this service with your OpenCDMP deployment, configure the deposit plugin in the OpenCDMP admin interface.

For detailed integration instructions, see see the [DSpace Configuration](https://opencdmp.github.io/getting-started/configuration/backend/deposit/#dspace) and the [OpenCDMP Deposit Service Authentication](https://opencdmp.github.io/getting-started/configuration/backend/#deposit-service-authentication).

---

## See Also

For complete documentation on configuration, integration, and usage:

- **Deposit Service Overview**: https://opencdmp.github.io/optional-services/deposit-services/
- **User Guide**: [Depositing Plans](https://opencdmp.github.io/user-guide/plans/deposit-a-plan/)
- **Developer Guide**: [Building Custom Deposit Services](https://opencdmp.github.io/developers/plugins/deposit/)

---

## License

This repository is licensed under the [EUPL 1.2 License](LICENSE).

### Contact

For questions, support, or feedback:

- **Email**: opencdmp at cite.gr
- **GitHub Issues**: https://github.com/OpenCDMP/repository-deposit-dspace/issues
---

*This service is part of the OpenCDMP ecosystem. For general OpenCDMP documentation, visit [opencdmp.github.io](https://opencdmp.github.io).*

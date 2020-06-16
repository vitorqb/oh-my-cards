## OhMyCards Server

### Setting Up

The following environmental variables can be used to configure the
application, and are expected to be set:

```bash
# Mailgun url used to send emails.
OHMYCARDS_MAILGUN_URL="https://api.mailgun.net/v3/foo.mailgun.org/messages"

# The "from" email when sending emails.
OHMYCARDS_MAILGUN_FROM="Info OhMyCards <mailgun@foo.mailgun.org>"

# The secret key to authenticate with mailgun
OHMYCARDS_MAILGUN_KEY="foobarbaz"

# The jdbc path to the db
OHMYCARDS_DB_DEFAULT_URL="jdbc:sqlite:dev.sqlite"

# A secret key.
OHMYCARDS_SECRET_KEY="supersecret"

# A port used to connect to ES
OHMYCARDS_ELASTICSEARCH_PORT="9200"

# The host used to connect to ES
OHMYCARDS_ELASTICSEARCH_HOST="127.0.0.1"

# A secret url, under which the admin commands are available.
OHMYCARDS_ADMIN_DASHBOARD_SECRET_URL="admin"
```


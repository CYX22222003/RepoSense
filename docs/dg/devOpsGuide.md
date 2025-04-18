{% set title = "DevOps Guide" %}
<frontmatter>
title: "{{ title | safe }}"
pageNav: 3
</frontmatter>

{% from 'scripts/macros.njk' import embed with context %}

<h1 class="display-4"><md>{{ title }}</md></h1>

<div class="lead">

This page documents the various components that form part of the DevOps infrastructure that RepoSense uses.

</div>

<!-- ==================================================================================================== -->

## GitHub Actions

[GitHub Actions](https://docs.github.com/en/actions) is a platform that is used by RepoSense for running the test suite and is primarily used for continuous integration and testing. The test suite is stored in `.github/workflows/` and comprises of:

- Unit tests (in `src/tests/`)
- System tests (in `src/systemtest/`)
- Frontend tests (in `frontend/cypress/tests/`)

### Continuous integration

All three types of tests in the test suite are run in a single GitHub Actions workflow called "Continuous Integration". The steps are defined in [`integration.yml`](https://github.com/reposense/RepoSense/blob/master/.github/workflows/integration.yml) and are split into two types of jobs:

1. Ubuntu/macOS/Windows JDK 11 (`mainbuild`): Runs both unit tests and system tests on JDK 11 running on supported Ubuntu, macOS and Windows versions. For Ubuntu, this job also produces a RepoSense report and the MarkBind documentation website for previewing.
2. Cypress frontend tests (`cypress`): Runs only the frontend tests on JDK 11 running on Ubuntu.

The list of supported OS versions are [available on the GitHub Docs website](https://docs.github.com/en/actions/using-github-hosted-runners/about-github-hosted-runners#supported-runners-and-hardware-resources). These jobs should be updated regularly whenever RepoSense supports newer versions of the Java Development Kit (JDK) as well as when new OS versions are made available (via the job OS matrix). Frontend tests are run separately to take advantage of parallel job execution.

Some of the jobs execute some commands that are too complicated to be included in the workflow configuration files. Such commands are written in the form of a bash script and are located in the [`config/gh-actions`](https://github.com/reposense/RepoSense/tree/master/config/gh-actions) folder.

This workflow is run for both incoming pull requests to any branch as well as direct commits to any branch in the repository.

Cypress frontend tests are run against reports generated from config files in `frontend/cypress/config`. It uses the `cypress` branch of the RepoSense repository which is kept independent of `master` and should be updated only when there are new frontend tests that need to be accommodated.

### Report and documentation previews

For each pull request to any branch in the repository, a RepoSense report and the MarkBind documentation website is generated based on the code submitted in the pull request. This is to facilitate pull request reviewers in being able to quickly preview how the RepoSense report and/or the documentation website will change after the pull request is merged.

Due to [security considerations in preventing pwn requests](https://securitylab.github.com/research/github-actions-preventing-pwn-requests), the deployment of the report and documentation previews were split across two workflows in [pull request #1411](https://github.com/reposense/RepoSense/pull/1411):

1. Surge.sh pending build (defined in [`pending.yml`](https://github.com/reposense/RepoSense/blob/master/.github/workflows/pending.yml)): Used for gathering information about the pull request and update the pull request checklist to indicate that the previews are pending.
2. Surge.sh build preview (defined in [`surge.yml`](https://github.com/reposense/RepoSense/blob/master/.github/workflows/surge.yml)): Used for actually deploying the RepoSense report and documentation website to Surge.sh and updating the pull request checklist to link to the preview websites.

The previews are recognised as GitHub deployments and are named `dashboard-$PRNUMBER` and `docs-$PRNUMBER`, where `$PRNUMBER` is the pull request number. Once a preview is ready, the reviewer would be able to click on "View deployment" or "Show environments" in the pull request to open the preview websites.

Both the "Surge.sh pending build" and "Continuous Integration" workflows produce an artifact respectively, which is then downloaded by the "Surge.sh build preview" workflow to be deployed to Surge.sh. Due to limitations in GitHub Actions in determining the workflow execution order, the "Surge.sh pending build" workflow is assumed to have been completed before the "Continuous Integration" workflow is completed.

This task is not performed on commits to the repository, as there is no need to do so.

After the pull request is closed or merged, the Surge.sh deployments are retained, while the GitHub environments for them are cleared by another workflow "Clear deployments for closed pull requests" (defined in [`delete-deploy.yml`](https://github.com/reposense/RepoSense/blob/master/.github/workflows/delete-deploy.yml)). The workflow uses [`delete-deploy.sh`](https://github.com/reposense/RepoSense/blob/master/config/gh-actions/delete-deploy.sh) (found in the `config/gh-actions/` folder) to remove the environments on GitHub, and to leave a comment on the pull request with links to the Surge.sh previews for later reference.

### GitHub Pages

This task builds the MarkBind documentation website on every push to the `master` branch. The steps are defined in [`gh-pages.yml`](https://github.com/reposense/RepoSense/blob/master/.github/workflows/gh-pages.yml).

### Stale pull requests

This task automates the cleaning up of the pull requests by automatically marking inactive pull requests as stale and subsequently closing them. The steps and configurations are defined in [`stale.yml`](https://github.com/reposense/RepoSense/blob/master/.github/workflows/stale.yml) and the job is triggered automatically once per day.

<!-- ==================================================================================================== -->

## Codecov

[Codecov](https://app.codecov.io/gh/reposense/RepoSense) is a platform for checking the code coverage status of the project and the pull request patch. It runs automatically on each pull request and the settings are located in [`codecov.yml`](https://github.com/reposense/RepoSense/blob/master/codecov.yml).

<!-- ==================================================================================================== -->

## Surge.sh

Surge.sh is a platform for hosting the RepoSense report and documentation preview builds. The deployment is automatically performed by the "Surge.sh build preview" job using the [`deploy.sh`](https://github.com/reposense/RepoSense/blob/master/config/gh-actions/deploy.sh) script in the `config/gh-actions/` folder.

For authentication, two secrets are stored and used in GitHub Actions:

1. `SURGE_LOGIN` - Holds the email address to use for the Surge.sh account
2. `SURGE_TOKEN` - Holds the secret account token generated for the above account (generated using `surge token`)

The `surge` command automatically detects the existence of these two environment variables and use them for authentication when deploying the RepoSense report and documentation build previews.

Currently, deployments are kept forever, even after the pull request is merged or closed. There is currently no existing functionality to automatically clean up deployments after they are no longer useful.

<!-- ==================================================================================================== -->

## Post-deployment test
Publish-RepoSense is our default tool for deploying RepoSense reports. We have automated the testing of deployments via this tool before each merge to strengthen our CI/CD processes. This is automatically performed by the job "GitHub Actions build preview" using the [`publish-preview.yml`](https://github.com/reposense/RepoSense/blob/master/config/gh-actions/publish-preview.yml).

There is a manual action named `GitHub Actions preview build manual` using the [`publish-preview-manual.yml`](https://github.com/reposense/RepoSense/blob/master/config/gh-actions/publish-preview-manual.yml). It takes in an input of the PR number and run the test on the branch referring to the PR.

The action will simulate a `jekyll build` environment using docker container on the GitHub action runner and audit the webpage using `lighthoucse-ci`, detecting potential issues in rendering the dashboard in actual github environments.

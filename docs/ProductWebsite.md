# Product website

The public product website introduces FacilityFlow AI, its three roles, the
maintenance lifecycle, advisory AI boundaries, documentation and team. This
supports the assignment's product-website deliverable and **NFR-DOC-001**.

## Files and hosting

- `docs/site/index.html`: product homepage.
- `docs/site/assets/`: local stylesheet and favicon; no external asset services.
- `.github/workflows/pages.yml`: package the website for pull requests and
  publish changes merged into `master` using GitHub Pages.

The site uses plain HTML and CSS, without a build step or JavaScript dependency.
The homepage follows MP2's product overview, roles, workflow, guides and source
structure, with new MP3 content and visual design. See the
[reuse ledger](../workflow/REUSE.md) for provenance.

Only `docs/site/` is uploaded. Guides link to their maintained Markdown versions
on GitHub; planning documents and other repository files are not copied into the
website artifact. Assets and section links are relative so the site works under
the repository's GitHub Pages path. The illustrative Request is not production
data or an application screenshot.

## Enable GitHub Pages

After teammate review and merging this branch into `master`:

1. Open the repository's **Settings → Pages**.
2. Under **Build and deployment → Source**, choose **GitHub Actions**.
3. Open **Actions → Publish product website** and run it on `master` if the
   automatic run happened before Pages was enabled.
4. Check that both jobs succeed and open the URL reported by the deployment.

Expected URL with default GitHub Pages settings:

<https://CS3227-2610-MP3-FacilityFlowAI.github.io/CS3227-2610-MP3/>

This is an expected address, not evidence of a successful deployment. The
workflow packages pull requests without deploying them. Manual dispatch from
another branch also packages only; deployment requires `master`. Configure the
`github-pages` environment to allow deployments from `master` if the environment
has branch restrictions. Publishing this static website does not deploy the
application or require application/database/LLM secrets.

GitHub's [custom Pages workflow documentation](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages)
describes the publishing source, artifact and environment requirements.

## Preview locally

From the repository root:

```text
python -m http.server 4173 --bind 127.0.0.1 --directory docs/site
```

Open <http://127.0.0.1:4173/>. Stop the server with Ctrl+C when finished.
The site can also be opened directly via `docs/site/index.html`.

When updating content, check the approved specifications and User Guide before
claiming a feature. Check desktop and narrow layouts, keyboard navigation,
section links, the live-app link and the GitHub resource links. Keep actual
verification evidence separate from deployment instructions.

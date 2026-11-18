# Project documentation

Start with the [O(1) Outpost project guide](./PROJECT-GUIDE.md) for the system
overview, service responsibilities, API map, data ownership, development, and
testing notes.

## Operations

- [Botkeep deployment](../deploy/README.md) — current production topology:
  five Botkeep workloads plus a `code-sandbox` host that calls the
  OnlineCompiler.io HTTPS API (no Docker VPS required).
- [Production deployment (legacy Docker/EC2)](./README-deployment.md) —
  single-host topology kept for reference.
- [EC2 setup (legacy)](./ec2-setup.md) — original EC2 provisioning guide.

# Time-to-Consistency in Saga Transactions: An Analytical Model and Empirical Validation

This repository contains all the materials to replicate the results shown in the article 
*Time-to-Consistency in Saga Transactions: An Analytical Model and Empirical Validation* 
submitted to **MASCOTS 2026**.

## Repository Structure

```
├── calculus/                    # Java project for time to consistency evaluation
├── images/
│   ├── saga-orchestrator.tar    # Orchestrator image
│   └── service-template.tar     # Service template image
└── README.md                    # This file
```

### `calculus/`

This folder contains the code for:
- *Workflow Model Generation*: starting from a set of topologies calculated from Alibaba traces and processing distribution, generates workflow in Java Objects and YAML file for kubernetes deployment
- *Time-To-Consistency Evaluation*: using our approach the Time-To-Consistency of the workflow is calculated and results saved
- *Topology Refactoring*: the topologies are refactored using the methodology showed

### `images/`

Contains the docker images useful for evaluating empirically the workflow in a minikube cluster.
- *saga-orchestrator.tar*, orchestrates the requests between the microservices
- *service-template.tar*, template for a microservice. It exposes apis for request in forward, backward and failure processing. It is used as image for Kubernetes deployment for empirical evaluation.
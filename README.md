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

## Prerequisites & Environment Setup
To load, analyze and generate yaml file the Java program uses Apache Maven for dependency management and build automation.

Requirements
- Java Development Kit (JDK): 24
- Build Tool: Apache Maven 3.9+
- Docker 20.10 or higher
- Minikube 1.37.0 or higher

## Setup

Build the java project using the script

```bash
./install.sh
```

Then extract the Docker images and load them in docker:

```bash
docker load -i images/saga-orchestrator.tar
docker load -i images/service-template.tar
```

## Experimentation

After building the project run the experimentation script:

```bash
./run_analysis.sh
```

This code will process all the topologies of the article and will store the results in:
- `analysis-results` for the analysis of the Time-To-Consistency of the workflows
- `refactoring-results` for the refactoring of the topologies

Then the empirical experimentation on a Minikube cluster can be executed using the following command:

```bash
./run_empirical_experimentation.sh
```

This script will execute the deployment and execution of the empirical experimentation for the workflows generated.
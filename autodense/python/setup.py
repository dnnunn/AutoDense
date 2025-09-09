#!/usr/bin/env python3
"""
AutoDense Python Package Setup
Heart transplant: Pure Python vision engine for laboratory image analysis
"""

from setuptools import setup, find_packages
from pathlib import Path

# Read requirements
requirements_path = Path(__file__).parent / "requirements.txt"
requirements = []
if requirements_path.exists():
    with open(requirements_path) as f:
        requirements = [line.strip() for line in f if line.strip() and not line.startswith('#')]

# Read long description
readme_path = Path(__file__).parent / "README.md"
long_description = "AutoDense Python vision engine for laboratory image analysis"
if readme_path.exists():
    with open(readme_path) as f:
        long_description = f.read()

setup(
    name="autodense-python",
    version="3.0.0",
    description="Pure Python vision engine for laboratory gel and colony analysis",
    long_description=long_description,
    long_description_content_type="text/markdown",
    author="AutoDense Development Team",
    author_email="autodense@betterdairy.com",
    url="https://github.com/betterdairy/autodense",
    packages=find_packages(),
    include_package_data=True,
    install_requires=requirements,
    python_requires=">=3.8",
    classifiers=[
        "Development Status :: 4 - Beta",
        "Intended Audience :: Science/Research",
        "License :: OSI Approved :: MIT License",
        "Programming Language :: Python :: 3",
        "Programming Language :: Python :: 3.8",
        "Programming Language :: Python :: 3.9",
        "Programming Language :: Python :: 3.10",
        "Programming Language :: Python :: 3.11",
        "Topic :: Scientific/Engineering :: Bio-Informatics",
        "Topic :: Scientific/Engineering :: Image Processing",
    ],
    keywords="gel electrophoresis colony analysis laboratory vision",
    entry_points={
        "console_scripts": [
            "autodense-analyze=autodense.cli:main",
            "autodense-bridge=autodense.service.bridge:main",
        ],
    },
)
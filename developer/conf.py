# Sphinx configuration for BerryCrush Developer Documentation
#
# This file configures Sphinx to build the developer/*.md files into HTML.

# -- Path setup --------------------------------------------------------------

import os
import sys

# -- Project information -----------------------------------------------------

project = 'BerryCrush Developer Guide'
copyright = '2026, BerryCrush Team'
author = 'BerryCrush Team'

# -- General configuration ---------------------------------------------------

extensions = [
    'myst_parser',
]

# MyST-Parser settings for Markdown processing
myst_enable_extensions = [
    'colon_fence',      # ::: fenced directives
    'deflist',          # Definition lists
    'tasklist',         # - [ ] task lists
]

myst_heading_anchors = 3  # Generate anchors for h1-h3

# Suffixes for source files
source_suffix = {
    '.rst': 'restructuredtext',
    '.md': 'markdown',
}

# The master toctree document
master_doc = 'index'

# Files to exclude from processing
exclude_patterns = ['_build', 'README.md', 'Thumbs.db', '.DS_Store']

# -- Options for HTML output -------------------------------------------------

html_theme = 'sphinx_rtd_theme'
html_theme_options = {
    'navigation_depth': 3,
    'collapse_navigation': False,
    'sticky_navigation': True,
}

# Custom sidebar templates
html_sidebars = {
    '**': [
        'globaltoc.html',
        'relations.html',
        'searchbox.html',
    ]
}

# -- Options for syntax highlighting -----------------------------------------

pygments_style = 'sphinx'
highlight_language = 'kotlin'

# -- Cross-references to other documentation ---------------------------------

# Allow referencing the user manual
intersphinx_mapping = {}

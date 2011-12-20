###
# Copyright © 2011 Diamond Light Source Ltd.
# Contact :  ScientificSoftware@diamond.ac.uk
# 
# This is free software: you can redistribute it and/or modify it under the
# terms of the GNU General Public License version 3 as published by the Free
# Software Foundation.
# 
# This software is distributed in the hope that it will be useful, but 
# WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
# Public License for more details.
# 
# You should have received a copy of the GNU General Public License along
# with this software. If not, see <http://www.gnu.org/licenses/>.
###

# -*- coding: utf-8 -*-

# get standard configurations settings
import os
if not 'BUILDERS_DOCUMENTATION' in os.environ:
    raise Exception, 'Environment variable BUILDERS_DOCUMENTATION must be set'
if not os.path.isabs(os.environ['BUILDERS_DOCUMENTATION']):
    raise Exception, 'Environment variable BUILDERS_DOCUMENTATION must be an absolute file path, but is "%s"' % (os.environ['BUILDERS_DOCUMENTATION'],) 
conf_common_path = os.path.join(os.environ['BUILDERS_DOCUMENTATION'], 'source', 'conf_common.py')
if not os.path.isfile(conf_common_path):
    raise Exception, 'File %s not found' % (conf_common_path,)
execfile(conf_common_path)

# General information about the project.
project = u'Diamond SDA Guide'
copyright = copyright_scisoft

# The short X.Y version.
version = '1.0'
# The full version, including alpha/beta/rc tags.
release = '1.0'
# The version number to append to the basename of PDF files (the part before the .pdf suffix) (an additional option for GDA and related projects)
version_for_filenames = '1.0'

# Theme options are theme-specific and customize the look and feel of a theme
# further.  For a list of options available for each theme, see the
# documentation.
html_theme_options = html_theme_options_scisoft

# The name of an image file (relative to this directory) to place at the top
# of the sidebar.
html_logo = html_logo_scisoft

# Grouping the document tree into LaTeX files. List of tuples
# (source start file, target name, title, author, documentclass [howto/manual]).
latex_documents = [
  ('contents', 'Diamond_SDA_Guide.tex', u'Diamond SDA Guide',
   _author_scisoft, 'manual'),
]

# The name of an image file (relative to this directory) to place at the top of
# the title page.
latex_logo = latex_logo_scisoft


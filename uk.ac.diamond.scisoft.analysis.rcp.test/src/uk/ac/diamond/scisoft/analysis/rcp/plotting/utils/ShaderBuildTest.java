/*-
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rcp.plotting.utils;

import org.eclipse.dawnsci.plotting.api.jreality.util.JOGLGLSLShaderGenerator;
import org.junit.Assert;
import org.junit.Test;
/**
 *
 */
public class ShaderBuildTest {

	private static final String FRAGPROG = 
		"uniform sampler2D sampler;\n"+
        "uniform sampler2D tableSampler;\n"+
        "uniform sampler2D overlaySampler;\n"+
        "uniform float maxValue;\n"+
        "uniform float minValue;\n"+
        "void main(void)\n"+
        "{\n"+
        " float dataValue = texture2D(sampler,gl_TexCoord[0].st).x;\n"+
        " float nDataValue = min(1.0,(dataValue - minValue) / (maxValue-minValue));\n"+
        " vec4 image = texture2D(tableSampler,vec2(nDataValue,nDataValue));\n"+
        " vec4 overlay = texture2D(overlaySampler, gl_TexCoord[0].st);\n"+
        " image = image * (1.0-overlay.w) + overlay * overlay.w;\n"+
        " gl_FragColor = image;\n"+
		"}\n";

    private static final String FRAGCOLORPASSTHROUGH =
    	"uniform sampler2D sampler;\n"+
    	"uniform sampler2D overlaySampler;\n"+
    	"void main(void)\n"+
    	"{\n"+
    	"vec4 image = texture2D(sampler,gl_TexCoord[0].st);\n"+
    	"vec4 overlay = texture2D(overlaySampler,gl_TexCoord[0].st);\n"+
    	"gl_FragColor = image;\n"+
    	"}\n";

	private static final String DIFFRAGPROG = 
		"uniform sampler2D sampler;\n"+
	    "uniform sampler2D tableSampler;\n"+
	    "uniform sampler2D overlaySampler;\n"+
	    "uniform float maxValue;\n"+
	    "uniform float minValue;\n"+
	    "uniform float threshold;\n"+
	    "void main(void)\n"+
	    "{\n"+
	    " float dataValue = texture2D(sampler,gl_TexCoord[0].st).x;\n"+
	    " float nDataValue = min(1.0,(dataValue - minValue) / (maxValue-minValue));\n"+
	    " vec4 image = texture2D(tableSampler,vec2(nDataValue,nDataValue));\n"+
	    " if (dataValue < -1.0) image = vec4(0.3,1.0,0.15,1.0);\n" +
	    " if (dataValue >= threshold) image = vec4(1,1,0,1);\n"+
        " vec4 overlay = texture2D(overlaySampler, gl_TexCoord[0].st);\n"+
        " image = image * (1.0-overlay.w) + overlay * overlay.w;\n"+
	    " gl_FragColor = image;\n"+
		"}\n";
	
	@Test
	public void buildNormalShader() {
		String shaderStr = JOGLGLSLShaderGenerator.generateShader(false, false, false,false);
		Assert.assertEquals(FRAGPROG, shaderStr);
		System.err.println(shaderStr);
	}
	
	@Test
	public void buildColourShader() {
		String shaderStr = JOGLGLSLShaderGenerator.generateShader(false, true, false,false);
		Assert.assertEquals(shaderStr, FRAGCOLORPASSTHROUGH);
	}
	
	@Test
	public void buildDiffractionShader() {
		String shaderStr = JOGLGLSLShaderGenerator.generateShader(false, false, true, false);
		Assert.assertEquals(shaderStr, DIFFRAGPROG);
		System.err.println(shaderStr);
	}
	
}

<!--
    Copyright (C) 2004 Orbeon, Inc.
  
    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.
  
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.
  
    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <!-- Convert and serialize to XML -->
    <p:processor name="oxf:xml-converter">
        <p:input name="config">
            <config>
                <encoding>utf-8</encoding>
            </config>
        </p:input>
        <p:input name="data">
            <document>
                <title>This is a very simple pipeline example</title>
            </document>
        </p:input>
        <p:output name="data" id="converted"/>
    </p:processor>

    <p:processor name="oxf:file-serializer">
        <p:input name="config">
            <config>
                <directory>c:/temp</directory>
                <file>test.xml</file>
                <append>false</append>
            </config>
        </p:input>
        <p:input name="data" href="#converted"/>
    </p:processor>

</p:config>

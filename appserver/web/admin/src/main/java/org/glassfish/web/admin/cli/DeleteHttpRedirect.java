/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.web.admin.cli;

import com.sun.enterprise.config.serverbeans.Config;
import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.util.LocalStringManagerImpl;
import com.sun.enterprise.util.SystemPropertyConstants;
import org.glassfish.grizzly.config.dom.NetworkConfig;
import org.glassfish.grizzly.config.dom.NetworkListener;
import org.glassfish.grizzly.config.dom.Protocol;
import org.glassfish.grizzly.config.dom.Protocols;
import org.glassfish.api.ActionReport;
import org.glassfish.api.I18n;
import org.glassfish.api.Param;
import org.glassfish.api.admin.AdminCommand;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.admin.RuntimeType;
import org.glassfish.api.admin.ServerEnvironment;
import org.glassfish.config.support.CommandTarget;
import org.glassfish.config.support.TargetType;
import org.glassfish.internal.api.Target;
import org.jvnet.hk2.annotations.Inject;
import org.jvnet.hk2.annotations.Scoped;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.component.Habitat;
import org.jvnet.hk2.component.PerLookup;
import org.jvnet.hk2.config.ConfigSupport;
import org.jvnet.hk2.config.SingleConfigCode;
import org.jvnet.hk2.config.TransactionFailure;

import java.util.List;

/**
 * Command to delete &lt;http-redirect&gt; elements within a specific
 * <code>protocol</code>.
 */
@Service(name = "delete-http-redirect")
@Scoped(PerLookup.class)
@I18n("delete.http.redirect")
@org.glassfish.api.admin.ExecuteOn({RuntimeType.DAS, RuntimeType.INSTANCE})
@TargetType({CommandTarget.DAS,CommandTarget.STANDALONE_INSTANCE,CommandTarget.CLUSTER,CommandTarget.CONFIG})
public class DeleteHttpRedirect implements AdminCommand {

    final private static LocalStringManagerImpl localStrings =
        new LocalStringManagerImpl(CreateHttp.class);

    @Param(name = "protocolname", primary = true)
    String protocolName;

    @Param(name = "target", optional = true, defaultValue = SystemPropertyConstants.DEFAULT_SERVER_INSTANCE_NAME)
    String target;

    @Inject(name = ServerEnvironment.DEFAULT_INSTANCE_NAME)
    Config config;

    @Inject
    Domain domain;

    @Inject
    Habitat habitat;


    // ----------------------------------------------- Methods from AdminCommand


    @Override
    public void execute(AdminCommandContext context) {
        Target targetUtil = habitat.getComponent(Target.class);
        Config newConfig = targetUtil.getConfig(target);
        if (newConfig != null) {
            config = newConfig;
        }
        Protocol protocolToBeRemoved = null;
        ActionReport report = context.getActionReport();
        NetworkConfig networkConfig = config.getNetworkConfig();
        Protocols protocols = networkConfig.getProtocols();
        try {
            for (Protocol protocol : protocols.getProtocol()) {
                if (protocolName.equalsIgnoreCase(protocol.getName())) {
                    protocolToBeRemoved = protocol;
                }
            }
            if (protocolToBeRemoved == null) {
                report.setMessage(localStrings.getLocalString(
                        "delete.http.notexists", "{0} http doesn't exist",
                        protocolName));
                report.setActionExitCode(ActionReport.ExitCode.FAILURE);
                return;
            }
            // check if the protocol whose http to be deleted is being used by
            // any network listener, then do not delete it.
            List<NetworkListener> nwlsnrList =
                    protocolToBeRemoved.findNetworkListeners();
            for (NetworkListener nwlsnr : nwlsnrList) {
                if (protocolToBeRemoved.getName().equals(nwlsnr.getProtocol())) {
                    report.setMessage(localStrings.getLocalString(
                            "delete.protocol.beingused",
                            "{0} protocol is being used in the network listener {1}",
                            protocolName, nwlsnr.getName()));
                    report.setActionExitCode(ActionReport.ExitCode.FAILURE);
                    return;
                }
            }
            ConfigSupport.apply(new SingleConfigCode<Protocol>() {
                public Object run(Protocol param) {
                    param.setHttpRedirect(null);
                    return null;
                }
            }, protocolToBeRemoved);

        } catch (TransactionFailure e) {
            report.setMessage(localStrings.getLocalString(
                    "delete.http.fail", "Deletion of http {0} failed",
                    protocolName) + "  " + e.getLocalizedMessage());
            report.setActionExitCode(ActionReport.ExitCode.FAILURE);
            report.setFailureCause(e);
            return;
        }
        report.setActionExitCode(ActionReport.ExitCode.SUCCESS);
    }

}

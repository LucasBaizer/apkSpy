package jadx.gui.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.OutputStream;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import com.lucasbaizer.ApkSpy;
import com.lucasbaizer.ChangeCache;
import com.lucasbaizer.ClassBreakdown;

import jadx.core.codegen.CodeWriter;
import jadx.gui.treemodel.JClass;
import jadx.gui.treemodel.JNode;
import jadx.gui.ui.codearea.AbstractCodeArea;
import jadx.gui.ui.codearea.CodeContentPanel;

public class EditMethodDialog extends JDialog {
	private static final long serialVersionUID = -8177172816914615698L;

	private AbstractCodeArea codeArea;

	public EditMethodDialog(MainWindow mainWindow, JNode jnode, JClass cls, AbstractCodeArea selectedCodeArea,
			EditParams params) {
		super(SwingUtilities.windowForComponent(mainWindow));

		JPanel content = new JPanel();

		TabbedPane pane = new TabbedPane(mainWindow);
		CodeContentPanel codeArea = new CodeContentPanel(pane, jnode);
		codeArea.setPreferredSize(new Dimension(800, 600));

		JScrollPane scroll = new JScrollPane(codeArea);
		pane.add(scroll);
		content.add(pane);

		JTextArea output = new JTextArea();
		JScrollPane scroll2 = new JScrollPane(output);
		Dimension size = scroll.getPreferredSize();
		size.width /= 2;
		scroll2.setPreferredSize(size);
		content.add(scroll2);

		JButton save = new JButton("Save");
		save.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				ClassBreakdown original = ClassBreakdown.breakdown(cls.getFullName(), cls.getContent());
				ClassBreakdown changed = ClassBreakdown.breakdown(cls.getFullName(),
						EditMethodDialog.this.codeArea.getText());

				ClassBreakdown completed = original.mergeImports(changed.getImports())
						.mergeMethods(changed.getChangedMethods());

				CodeWriter writer = new CodeWriter();
				writer.add(completed.toString());
				writer = writer.finish();
				cls.getCls().getClassNode().setCode(writer);

				int caret = selectedCodeArea.getCaretPosition();
				selectedCodeArea.setText(completed.toString());

				selectedCodeArea.setCaretPosition(caret);

				ChangeCache.putChange(cls.getFullName(), changed.mergeMemberVariables(original.getMemberVariables())
						.mergeMethodStubs(original.getMethods()), changed.getMethods().get(0));

				dispose();
			}
		});
		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				dispose();
			}
		});
		JButton compile = new JButton("Compile");
		compile.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				output.setText("");

				Thread thread = new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							ClassBreakdown original = ClassBreakdown.breakdown(cls.getFullName(), cls.getContent());
							ClassBreakdown changed = ClassBreakdown.breakdown(cls.getFullName(),
									EditMethodDialog.this.codeArea.getText());

							if (ApkSpy.lint(mainWindow.getProject().getFilePath().toString(), cls.getFullName(),
									changed.mergeMemberVariables(original.getMemberVariables())
											.mergeMethodStubs(original.getMethods()),
									new OutputStream() {
										@Override
										public void write(int b) throws IOException {
											System.out.print((char) b);
											output.append(Character.toString((char) b));
										}
									})) {
								output.append("Successfully compiled!\n");
							} else {
								output.append("Encounted errors while compiling!\n");
							}
						} catch (IOException | InterruptedException ex) {
							ex.printStackTrace();
						}
					}
				});
				thread.start();

			}
		});

		JPanel buttons = new JPanel();
		buttons.add(compile);
		buttons.add(save);
		buttons.add(cancel);

		add(buttons, BorderLayout.PAGE_START);
		add(content, BorderLayout.PAGE_END);

		setTitle("Edit Method");
		pack();
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setModalityType(ModalityType.APPLICATION_MODAL);
		setModal(true);
		setLocationRelativeTo(null);

		this.codeArea = codeArea.getCodeArea();
		this.codeArea.setEditable(true);
		this.codeArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);

		this.codeArea.requestFocus();
	}

	public void setCodeAreaContent(String content) {
		codeArea.setText(content);
	}

	public static class EditParams {
		public int headStart;
		public int headEnd;
		public int methodStart;
		public int methodEnd;

		public EditParams(int headStart, int headEnd, int methodStart, int methodEnd) {
			this.headStart = headStart;
			this.headEnd = headEnd;
			this.methodStart = methodStart;
			this.methodEnd = methodEnd;
		}
	}
}

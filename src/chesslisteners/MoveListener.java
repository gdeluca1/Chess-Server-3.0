/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package chesslisteners;

import chess.Chess;
import chess.ChessBoard;
import chess.ChessMove;
import chess.ChessPiece;
import chess.ChessPieceBox;
import chess.ChessPieceChooser;
import chessmaps.ChessPlayer;
import chess.ChessSquare;
import chess.ChessSquareBox;
import chessmaps.ColorMap;
import chess.Console;
import chess.IO;
import chessmaps.PieceMap;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;
import messaging.InputThread;

    

/**
 *
 * @author Gennaro
 */
public class MoveListener implements MouseListener
    {
        private ChessPiece selectedPiece;
        
        private ChessPieceBox chessPieceBox;
        private ChessSquareBox chessSquareBox;
        private static boolean inCheck = false;
        
        /**
        * For connections.
        */
       final static int PORT = 6789;
       ServerSocket serverSocket = null;
       Socket socket = null;
       
       private static boolean yourTurn;
       private boolean firstTime = true;

       public MoveListener()
       {
           try
           {
               serverSocket = new ServerSocket(PORT);
               
                while(socket == null)
                {
                    socket = serverSocket.accept();
                }
           }
           catch(IOException ex)
           {
               System.out.println("Error creating server socket/connecting MoveListener:71");
           }
            
            if(socket != null)
            {
                Console.admin.println("Successfully connected to client.");
            }
            
            chessPieceBox = ChessPieceBox.getInstance();
            chessSquareBox = ChessSquareBox.getInstance();
            
            yourTurn = (chessPieceBox.getPlayer() == ChessPlayer.white) ? true : false;
       }
       
       public void startThread()
       {
            new Thread()
            {
                @Override
                public void start()
                {
                    // Server always goes first, so it must first wait for a move.
                    if(firstTime)
                    {
                        firstTime = false;
                        try
                        {
                            InputStreamReader newisr = new InputStreamReader(socket.getInputStream());
                            BufferedReader reader = new BufferedReader(newisr);

                            String input = reader.readLine();
                            IO.deserialize(input);

                            yourTurn = true;
                        }
                        catch(IOException ex)
                        {
                            ex.printStackTrace();
                        }
                    }
                }     
            }.start();
       }
       
        @Override
        public void mousePressed(MouseEvent e)
        {
            // Request focus for the menu bar so that the keyboard shortcuts still work.
            Chess.getGMenuBar().getMenuBar().requestFocus();
            
            // If there was a connection issue, stop all play.
            if(!InputThread.isOpen())
            {
                e.consume();
                return;
            }
            
            if(yourTurn) 
            {
                // Case: a square was just pressed with no piece already selected.
                if(selectedPiece == null)
                {
                    // A piece was on that clicked square.
                    selectedPiece = chessPieceBox.getPieceFromPixels(e.getX(), e.getY());
                    
                    // Safety net in case a bug causes a null piece to be selected.
                    if(selectedPiece != null)   
                    {   
                        // Confirm color since you can only move your own pieces.
                        if(((chessPieceBox.getPlayer() == ChessPlayer.white && selectedPiece.getPieceColor()) == PieceMap.WHITE) || 
                            (chessPieceBox.getPlayer() ==  ChessPlayer.black && selectedPiece.getPieceColor() == PieceMap.BLACK)) 
                        {
                            selectedPiece.getSquare().setColor(ColorMap.SELECTED);
                            Chess.getChessBoard().repaint();
                        }
                        
                        else
                        {
                            // If it's the wrong color, unselect it.
                            selectedPiece = null;
                        }
                    }
                }
                // The square with the same piece on it was pressed
                else if(chessSquareBox.findSquareFromPixels(e.getX(), e.getY()) == selectedPiece.getSquare())
                {
                    // Release the held piece.
                    selectedPiece.getSquare().resetColor();
                    selectedPiece = null;
                    Chess.getChessBoard().repaint();
                }
                // A different square was pressed with a piece selected.
                else
                {
                    // Prepare to move the piece.
                    int newX = e.getX()*8/Chess.getChessBoard().getPanelWidth();
                    int newY = e.getY()*8/Chess.getChessBoard().getPanelHeight();
                    
                    boolean isCastling;
                    
                    // TODO: admin mode
                    // Check to see if it can actually move.
              /*      if(Chess.getChessBoard().adminModeEnabled)
                    {
                        if(getPieceFromSquare(newX, newY) != null)
                        {
                            getPieceFromSquare(newX, newY).remove();
                        }
                        selectedPiece.getSquare().setX(e.getX()*8/(panelWidth));
                        selectedPiece.getSquare().setY(e.getY()*8/(panelHeight));
                        containingSquare.setColor(containingSquare.originalColor);
                        selectedPiece.hasMoved = true;
                        selectedPiece = null;
                        repaint();
                    } */
                    // Confirm that the piece can move to that square. (Includes a check for castling).
                    if((isCastling = ChessMove.canCastle(selectedPiece, chessSquareBox.findSquare(newX, newY))) || 
                            ChessMove.canMove(selectedPiece, chessSquareBox.findSquare(newX, newY)))
                    {
                        chessSquareBox.resetSquareColors();
                        
                        // Handle castle.
                        if(isCastling)
                        {
                            ChessSquare targetSquare = chessSquareBox.findSquare(newX, newY);
                            ChessPiece rook;
                            // Basic math to get rook movement. Varies depending on if it's a king side or queen side castle.
                            switch(ChessMove.castleSide) {
                                case ChessMove.KING_SIDE:
                                    rook = chessPieceBox.getPieceFromSquare(targetSquare.getXPos() + (targetSquare.getXPos() - selectedPiece.getSquare().getXPos())/2, 
                                            targetSquare.getYPos());
                                    rook.setSquare(targetSquare.getXPos() - (targetSquare.getXPos() - selectedPiece.getSquare().getXPos())/2 , rook.getSquare().getYPos());
                                    break;
                                case ChessMove.QUEEN_SIDE:
                                    rook = chessPieceBox.getPieceFromSquare(targetSquare.getXPos() + (targetSquare.getXPos() - selectedPiece.getSquare().getXPos()), 
                                            targetSquare.getYPos());
                                    rook.setSquare(targetSquare.getXPos() - (targetSquare.getXPos() - selectedPiece.getSquare().getXPos())/2 , rook.getSquare().getYPos());
                                    break;
                            }
                        }
                        
                        // If there's a piece that you're attacking, remove it.
                        if(chessPieceBox.getPieceFromSquare(newX, newY) != null)
                        {
                            chessPieceBox.getPieceFromSquare(newX, newY).remove();
                        }
                        
                        // Move the piece to the selected square.
                        selectedPiece.setSquare(e.getX()*8/Chess.getChessBoard().getPanelWidth(), e.getY()*8/Chess.getChessBoard().getPanelHeight());
                        
                        // Handle a pawn reaching the end of the board here. 
                        if(selectedPiece.getPieceType() == PieceMap.PAWN && (selectedPiece.getSquare().getYPos() == 0 || selectedPiece.getSquare().getYPos() == 7))
                        {
                            ChessPieceChooser pieceChooser = new ChessPieceChooser(null, true);
                            pieceChooser.setVisible(true);
                            while(pieceChooser.isVisible())
                            {
                                // Wait.
                            }
                            selectedPiece.changePiece(Chess.getChessBoard().getPanelWidth(), Chess.getChessBoard().getPanelHeight(), chessPieceBox.getSelectedPiece());
                        }
                        
                        selectedPiece.setMoved();
                        selectedPiece = null;
                        Chess.getChessBoard().repaint();
                        
                        // Handle socket data transfer here.
                        
                        new Thread() {
                            
                            @Override
                            public void run()
                            {
                                while(socket.isClosed())
                                {
                                    try {
                                        Console.err.println("Probably shouldn't be on line 238 of MoveListener...");
                                        socket = serverSocket.accept();
                                    } catch (IOException ex) {
                                        Logger.getLogger(ChessBoard.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                                }
                                 if(socket != null && !socket.isClosed())
                                    {
                                    DataOutputStream out;
                                    try {
                                        out = new DataOutputStream(socket.getOutputStream());
                                        
                                        out.writeBytes(IO.serialize());
                                        
                                        out.flush();
                                        
                                        yourTurn = false;
                                        
                                        InputStreamReader newisr = new InputStreamReader(socket.getInputStream());
                                        BufferedReader reader = new BufferedReader(newisr);
                                        
                                        String input = reader.readLine();
                                        IO.deserialize(input);
                                        Chess.getChessBoard().repaint();
                                        inCheck = ChessMove.isChecked();
                                        
                                        yourTurn = true;
                                    } catch (IOException ex) {
                                        Logger.getLogger(ChessBoard.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                                } 
                            }
                        }.start();
                    }
                    else // If you try ot go somewhere you can't, unselect the piece.
                    {
                        selectedPiece.getSquare().resetColor();
                        selectedPiece = null;
                        Chess.getChessBoard().repaint();
                    }
                }
            }
            else
            {
                e.consume();
            }
        }
        
        @Override 
        public void mouseReleased(MouseEvent e)
        {
        }
        
        @Override
        public void mouseClicked(MouseEvent e)
        {
            
        }   
        
        @Override
        public void mouseEntered(MouseEvent e)
        {
            
        }
        
        @Override
        public void mouseExited(MouseEvent e)
        {
            
        }

        public static boolean isYourTurn()
        {
            return yourTurn;
        }
        
        public static boolean isChecked()
        {
            return inCheck;
        }
    }